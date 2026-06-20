package com.lecturemate.model;

import java.time.LocalDateTime;

/**
 * Stores user-written notes that belong to a lecture.
 */
public class Note {

    private long id;
    private long lectureId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Note() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Note(long lectureId, String content) {
        this();
        this.lectureId = lectureId;
        this.content = content;
    }

    public Note(long id, long lectureId, String content, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.lectureId = lectureId;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        if (content == null || content.isBlank()) {
            return "빈 필기";
        }
        return content.length() > 40 ? content.substring(0, 40) + "..." : content;
    }
}
