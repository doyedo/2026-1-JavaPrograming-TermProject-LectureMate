package com.lecturemate.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Creates SQLite connections and keeps the required LectureMate schema available.
 */
public final class DatabaseManager {

    private static final String DATABASE_FILE_NAME = "lecturemate.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DATABASE_FILE_NAME;

    private DatabaseManager() {
    }

    /**
     * Opens a SQLite connection and enables foreign key support for that connection.
     */
    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(JDBC_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /**
     * Creates tables for a fresh database and lightly migrates older project schemas.
     */
    public static void initializeDatabase() {
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS lectures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT,
                        subject TEXT,
                        audio_path TEXT,
                        transcript TEXT,
                        summary TEXT,
                        summary_style TEXT,
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lecture_id INTEGER,
                        title TEXT,
                        description TEXT,
                        schedule_date TEXT,
                        schedule_time TEXT,
                        type TEXT,
                        is_done INTEGER DEFAULT 0,
                        FOREIGN KEY (lecture_id) REFERENCES lectures(id) ON DELETE CASCADE
                    )
                    """);

            // Existing earlier builds may still contain notes; keep it harmless for compatibility.
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lecture_id INTEGER,
                        content TEXT,
                        created_at TEXT,
                        updated_at TEXT,
                        FOREIGN KEY (lecture_id) REFERENCES lectures(id) ON DELETE CASCADE
                    )
                    """);

            migrateOlderSchema(connection, statement);
            rebuildSchedulesTableIfNeeded(connection, statement);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_schedules_lecture_done ON schedules(lecture_id, is_done)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_schedules_lecture_date ON schedules(lecture_id, schedule_date)");
        } catch (SQLException exception) {
            throw new IllegalStateException("SQLite 테이블을 초기화할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    private static void migrateOlderSchema(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "lectures", "audio_path", "TEXT");
        addColumnIfMissing(connection, statement, "lectures", "transcript", "TEXT");
        addColumnIfMissing(connection, statement, "lectures", "summary", "TEXT");
        addColumnIfMissing(connection, statement, "lectures", "summary_style", "TEXT");
        addColumnIfMissing(connection, statement, "lectures", "created_at", "TEXT");
        addColumnIfMissing(connection, statement, "lectures", "updated_at", "TEXT");

        if (hasColumn(connection, "lectures", "recording_path")) {
            statement.execute("""
                    UPDATE lectures
                    SET audio_path = COALESCE(NULLIF(audio_path, ''), recording_path)
                    WHERE recording_path IS NOT NULL
                    """);
        }

        addColumnIfMissing(connection, statement, "schedules", "lecture_id", "INTEGER");
        addColumnIfMissing(connection, statement, "schedules", "title", "TEXT");
        addColumnIfMissing(connection, statement, "schedules", "description", "TEXT");
        addColumnIfMissing(connection, statement, "schedules", "schedule_date", "TEXT");
        addColumnIfMissing(connection, statement, "schedules", "schedule_time", "TEXT");
        addColumnIfMissing(connection, statement, "schedules", "type", "TEXT");
        addColumnIfMissing(connection, statement, "schedules", "is_done", "INTEGER DEFAULT 0");
        copyColumnIfPresent(connection, statement, "schedules", "date", "schedule_date");
        copyColumnIfPresent(connection, statement, "schedules", "time", "schedule_time");
        if (hasColumn(connection, "schedules", "completed")) {
            statement.execute("""
                    UPDATE schedules
                    SET is_done = COALESCE(is_done, completed)
                    WHERE completed IS NOT NULL
                    """);
        }
    }

    private static void rebuildSchedulesTableIfNeeded(Connection connection, Statement statement) throws SQLException {
        Set<String> expectedColumns = Set.of(
                "id",
                "lecture_id",
                "title",
                "description",
                "schedule_date",
                "schedule_time",
                "type",
                "is_done");
        Set<String> actualColumns = getColumnNames(connection, "schedules");
        if (actualColumns.equals(expectedColumns)) {
            return;
        }

        boolean originalAutoCommit = connection.getAutoCommit();
        statement.execute("PRAGMA foreign_keys = OFF");
        connection.setAutoCommit(false);
        try {
            statement.execute("DROP TABLE IF EXISTS schedules_legacy_migration");
            statement.execute("ALTER TABLE schedules RENAME TO schedules_legacy_migration");
            statement.execute("""
                    CREATE TABLE schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lecture_id INTEGER,
                        title TEXT,
                        description TEXT,
                        schedule_date TEXT,
                        schedule_time TEXT,
                        type TEXT,
                        is_done INTEGER DEFAULT 0,
                        FOREIGN KEY (lecture_id) REFERENCES lectures(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    INSERT INTO schedules
                        (id, lecture_id, title, description, schedule_date, schedule_time, type, is_done)
                    SELECT
                        id,
                        lecture_id,
                        title,
                        description,
                        COALESCE(NULLIF(schedule_date, ''), date('now')),
                        COALESCE(NULLIF(schedule_time, ''), '09:00'),
                        COALESCE(NULLIF(type, ''), '기타'),
                        COALESCE(is_done, 0)
                    FROM schedules_legacy_migration
                    """);
            statement.execute("DROP TABLE schedules_legacy_migration");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }

    private static void copyColumnIfPresent(
            Connection connection,
            Statement statement,
            String tableName,
            String oldColumnName,
            String newColumnName) throws SQLException {
        if (hasColumn(connection, tableName, oldColumnName) && hasColumn(connection, tableName, newColumnName)) {
            statement.execute("UPDATE " + tableName
                    + " SET " + newColumnName + " = COALESCE(NULLIF(" + newColumnName + ", ''), " + oldColumnName + ")"
                    + " WHERE " + oldColumnName + " IS NOT NULL");
        }
    }

    private static void addColumnIfMissing(
            Connection connection,
            Statement statement,
            String tableName,
            String columnName,
            String definition) throws SQLException {
        if (!hasColumn(connection, tableName, columnName)) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = connection.createStatement().executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> getColumnNames(Connection connection, String tableName) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet resultSet = connection.createStatement().executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name").toLowerCase());
            }
        }
        return names;
    }

    public static String getDatabaseFileName() {
        return DATABASE_FILE_NAME;
    }
}
