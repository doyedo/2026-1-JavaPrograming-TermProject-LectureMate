package com.lecturemate.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Incomplete and completed lecture schedules stored in the schedules table.
 */
public class Schedule {

    private long id;
    private long lectureId;
    private String title;
    private String description;
    private LocalDate scheduleDate;
    private LocalTime scheduleTime;
    private String type;
    private boolean done;

    public Schedule() {
        this.scheduleDate = LocalDate.now();
        this.scheduleTime = LocalTime.of(9, 0);
        this.type = "복습";
    }

    public Schedule(long lectureId, String title, LocalDate scheduleDate, LocalTime scheduleTime, String type) {
        this();
        this.lectureId = lectureId;
        this.title = title;
        this.scheduleDate = scheduleDate;
        this.scheduleTime = scheduleTime;
        this.type = type;
    }

    public Schedule(
            long id,
            long lectureId,
            String title,
            String description,
            LocalDate scheduleDate,
            LocalTime scheduleTime,
            String type,
            boolean done) {
        this.id = id;
        this.lectureId = lectureId;
        this.title = title;
        this.description = description;
        this.scheduleDate = scheduleDate;
        this.scheduleTime = scheduleTime;
        this.type = type;
        this.done = done;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getLectureId() {
        return lectureId;
    }

    public void setLectureId(long lectureId) {
        this.lectureId = lectureId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public LocalTime getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(LocalTime scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    @Override
    public String toString() {
        String status = done ? "[완료]" : "[예정]";
        return status + " " + scheduleDate + " " + scheduleTime + " " + title;
    }
}
