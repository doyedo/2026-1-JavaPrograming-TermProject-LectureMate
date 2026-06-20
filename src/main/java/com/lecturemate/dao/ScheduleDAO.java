package com.lecturemate.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.lecturemate.model.Schedule;
import com.lecturemate.util.DateUtil;

/**
 * DAO for schedule CRUD and incomplete-schedule calendar queries.
 */
public class ScheduleDAO {

    public Schedule insert(Schedule schedule) {
        String sql = """
                INSERT INTO schedules
                    (lecture_id, title, description, schedule_date, schedule_time, type, is_done)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindSchedule(statement, schedule);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    schedule.setId(keys.getLong(1));
                }
            }
            return schedule;
        } catch (SQLException exception) {
            throw new IllegalStateException("일정을 저장할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public List<Schedule> findIncompleteByLectureId(long lectureId) {
        String sql = """
                SELECT * FROM schedules
                WHERE lecture_id = ? AND COALESCE(is_done, 0) = 0
                ORDER BY date(schedule_date), time(schedule_time), id
                """;
        return findSchedules(sql, lectureId);
    }

    public List<Schedule> findIncompleteByLectureIdAndDate(long lectureId, LocalDate date) {
        String sql = """
                SELECT * FROM schedules
                WHERE lecture_id = ? AND schedule_date = ? AND COALESCE(is_done, 0) = 0
                ORDER BY time(schedule_time), id
                """;
        List<Schedule> schedules = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lectureId);
            statement.setString(2, DateUtil.toDateString(date));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    schedules.add(mapSchedule(resultSet));
                }
            }
            return schedules;
        } catch (SQLException exception) {
            throw new IllegalStateException("선택 날짜의 일정을 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public List<LocalDate> findScheduleDatesByLectureId(long lectureId) {
        String sql = """
                SELECT DISTINCT schedule_date FROM schedules
                WHERE lecture_id = ? AND COALESCE(is_done, 0) = 0
                ORDER BY date(schedule_date)
                """;
        Set<LocalDate> dates = new LinkedHashSet<>();
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lectureId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LocalDate date = DateUtil.parseDate(resultSet.getString("schedule_date"));
                    if (date != null) {
                        dates.add(date);
                    }
                }
            }
            return new ArrayList<>(dates);
        } catch (SQLException exception) {
            throw new IllegalStateException("일정 날짜를 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public boolean updateIsDone(long scheduleId, boolean done) {
        String sql = "UPDATE schedules SET is_done = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, done ? 1 : 0);
            statement.setLong(2, scheduleId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("일정 완료 여부를 저장할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM schedules WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("일정을 삭제할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public boolean update(Schedule schedule) {
        String sql = """
                UPDATE schedules
                SET lecture_id = ?, title = ?, description = ?, schedule_date = ?,
                    schedule_time = ?, type = ?, is_done = ?
                WHERE id = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSchedule(statement, schedule);
            statement.setLong(8, schedule.getId());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("일정을 수정할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    public Optional<Schedule> findById(long id) {
        String sql = "SELECT * FROM schedules WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapSchedule(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new IllegalStateException("일정을 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    private List<Schedule> findSchedules(String sql, long lectureId) {
        List<Schedule> schedules = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lectureId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    schedules.add(mapSchedule(resultSet));
                }
            }
            return schedules;
        } catch (SQLException exception) {
            throw new IllegalStateException("일정 목록을 불러올 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    private void bindSchedule(PreparedStatement statement, Schedule schedule) throws SQLException {
        statement.setLong(1, schedule.getLectureId());
        statement.setString(2, schedule.getTitle());
        statement.setString(3, schedule.getDescription());
        statement.setString(4, DateUtil.toDateString(schedule.getScheduleDate()));
        statement.setString(5, DateUtil.toTimeString(schedule.getScheduleTime()));
        statement.setString(6, schedule.getType());
        statement.setInt(7, schedule.isDone() ? 1 : 0);
    }

    private Schedule mapSchedule(ResultSet resultSet) throws SQLException {
        return new Schedule(
                resultSet.getLong("id"),
                resultSet.getLong("lecture_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                DateUtil.parseDate(resultSet.getString("schedule_date")),
                DateUtil.parseTime(resultSet.getString("schedule_time")),
                resultSet.getString("type"),
                resultSet.getInt("is_done") == 1);
    }
}
