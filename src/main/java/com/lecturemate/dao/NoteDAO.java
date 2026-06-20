package com.lecturemate.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.lecturemate.model.Note;

/**
 * Provides CRUD operations for user notes.
 */
public class NoteDAO {

    /**
     * Inserts a note and updates the model with the generated database id.
     */
    public Note insert(Note note) {
        String sql = """
                INSERT INTO notes (lecture_id, content, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """;

        LocalDateTime now = LocalDateTime.now();
        if (note.getCreatedAt() == null) {
            note.setCreatedAt(now);
        }
        if (note.getUpdatedAt() == null) {
            note.setUpdatedAt(now);
        }

        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindNote(statement, note);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    note.setId(keys.getLong(1));
                }
            }
            return note;
        } catch (SQLException exception) {
            throw new IllegalStateException("필기를 저장할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    /**
     * Returns all notes with newest items first.
     */
    public List<Note> findAll() {
        String sql = "SELECT * FROM notes ORDER BY datetime(updated_at) DESC, id DESC";
        List<Note> notes = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                notes.add(mapNote(resultSet));
            }
            return notes;
        } catch (SQLException exception) {
            throw new IllegalStateException("필기 목록을 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    /**
     * Finds a note by primary key.
     */
    public Optional<Note> findById(long id) {
        String sql = "SELECT * FROM notes WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapNote(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("필기를 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    /**
     * Returns every note belonging to a lecture.
     */
    public List<Note> findByLectureId(long lectureId) {
        String sql = "SELECT * FROM notes WHERE lecture_id = ? ORDER BY datetime(updated_at) DESC, id DESC";
        List<Note> notes = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lectureId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    notes.add(mapNote(resultSet));
                }
            }
            return notes;
        } catch (SQLException exception) {
            throw new IllegalStateException("강의별 필기를 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    /**
     * Updates the note content and timestamp.
     */
    public boolean update(Note note) {
        String sql = """
                UPDATE notes
                SET lecture_id = ?,
                    content = ?,
                    created_at = ?,
                    updated_at = ?
                WHERE id = ?
                """;

        note.setUpdatedAt(LocalDateTime.now());

        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNote(statement, note);
            statement.setLong(5, note.getId());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("필기를 수정할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    /**
     * Deletes a note by primary key.
     */
    public boolean delete(long id) {
        String sql = "DELETE FROM notes WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("필기를 삭제할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    private void bindNote(PreparedStatement statement, Note note) throws SQLException {
        statement.setLong(1, note.getLectureId());
        statement.setString(2, note.getContent());
        statement.setString(3, formatDateTime(note.getCreatedAt()));
        statement.setString(4, formatDateTime(note.getUpdatedAt()));
    }

    private Note mapNote(ResultSet resultSet) throws SQLException {
        return new Note(
                resultSet.getLong("id"),
                resultSet.getLong("lecture_id"),
                resultSet.getString("content"),
                parseDateTime(resultSet.getString("created_at")),
                parseDateTime(resultSet.getString("updated_at")));
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? LocalDateTime.now().toString() : value.toString();
    }

    private LocalDateTime parseDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }
}
