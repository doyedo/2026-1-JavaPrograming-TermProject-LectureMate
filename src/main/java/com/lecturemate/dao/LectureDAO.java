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

import com.lecturemate.model.Lecture;
import com.lecturemate.util.DateUtil;

/**
 * DAO for CRUD operations on lectures.
 */
public class LectureDAO {

    /**
     * Inserts a lecture and returns the same object with its generated id.
     */
    public Lecture insert(Lecture lecture) {
        String sql = """
                INSERT INTO lectures (title, subject, audio_path, transcript, summary, summary_style, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        LocalDateTime now = LocalDateTime.now();
        if (lecture.getCreatedAt() == null) {
            lecture.setCreatedAt(now);
        }
        if (lecture.getUpdatedAt() == null) {
            lecture.setUpdatedAt(now);
        }

        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindLecture(statement, lecture);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    lecture.setId(keys.getLong(1));
                }
            }
            return lecture;
        } catch (SQLException exception) {
            throw new IllegalStateException("강의를 저장할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public List<Lecture> findAll() {
        String sql = "SELECT * FROM lectures ORDER BY datetime(created_at) DESC, id DESC";
        List<Lecture> lectures = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                lectures.add(mapLecture(resultSet));
            }
            return lectures;
        } catch (SQLException exception) {
            throw new IllegalStateException("강의 목록을 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public Optional<Lecture> findById(long id) {
        String sql = "SELECT * FROM lectures WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapLecture(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("강의를 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public boolean update(Lecture lecture) {
        String sql = """
                UPDATE lectures
                SET title = ?, subject = ?, audio_path = ?, transcript = ?, summary = ?, summary_style = ?, created_at = ?, updated_at = ?
                WHERE id = ?
                """;
        lecture.setUpdatedAt(LocalDateTime.now());
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLecture(statement, lecture);
            statement.setLong(9, lecture.getId());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("강의를 수정할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM lectures WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("강의를 삭제할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    /**
     * Updates only the transcript field after STT or direct editing.
     */
    public boolean updateTranscript(long id, String transcript) {
        return updateSingleTextColumn(id, "transcript", transcript);
    }

    /**
     * Updates only the final summary/note field.
     */
    public boolean updateSummary(long id, String summary) {
        return updateSummary(id, summary, null);
    }

    /**
     * Updates the final note text and its rich-text style data together.
     */
    public boolean updateSummary(long id, String summary, String summaryStyle) {
        String sql = "UPDATE lectures SET summary = ?, summary_style = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, summary);
            statement.setString(2, summaryStyle);
            statement.setString(3, DateUtil.nowDateTimeString());
            statement.setLong(4, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("정리 노트 스타일을 저장할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    private boolean updateSingleTextColumn(long id, String columnName, String value) {
        String sql = "UPDATE lectures SET " + columnName + " = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setString(2, DateUtil.nowDateTimeString());
            statement.setLong(3, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("강의 내용을 저장할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    private void bindLecture(PreparedStatement statement, Lecture lecture) throws SQLException {
        statement.setString(1, lecture.getTitle());
        statement.setString(2, lecture.getSubject());
        statement.setString(3, lecture.getAudioPath());
        statement.setString(4, lecture.getTranscript());
        statement.setString(5, lecture.getSummary());
        statement.setString(6, lecture.getSummaryStyle());
        statement.setString(7, DateUtil.toDateTimeString(lecture.getCreatedAt()));
        statement.setString(8, DateUtil.toDateTimeString(lecture.getUpdatedAt()));
    }

    private Lecture mapLecture(ResultSet resultSet) throws SQLException {
        return new Lecture(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("subject"),
                resultSet.getString("audio_path"),
                resultSet.getString("transcript"),
                resultSet.getString("summary"),
                resultSet.getString("summary_style"),
                DateUtil.parseDateTime(resultSet.getString("created_at")),
                DateUtil.parseDateTime(resultSet.getString("updated_at")));
    }
}
