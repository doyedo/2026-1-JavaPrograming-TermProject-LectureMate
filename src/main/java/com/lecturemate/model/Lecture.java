package com.lecturemate.model;

import java.time.LocalDateTime;

/**
 * Lecture aggregate stored in the lectures table.
 * The summary field doubles as the generated summary and the user's final edited note.
 */
public class Lecture {

    private long id;
    private String title;
    private String subject;
    private String audioPath;
    private String transcript;
    private String summary;
    private String summaryStyle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Lecture() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Lecture(String title, String subject) {
        this();
        this.title = title;
        this.subject = subject;
    }

    public Lecture(
            long id,
            String title,
            String subject,
            String audioPath,
            String transcript,
            String summary,
            String summaryStyle,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.subject = subject;
        this.audioPath = audioPath;
        this.transcript = transcript;
        this.summary = summary;
        this.summaryStyle = summaryStyle;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSummaryStyle() {
        return summaryStyle;
    }

    public void setSummaryStyle(String summaryStyle) {
        this.summaryStyle = summaryStyle;
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
        String safeTitle = title == null || title.isBlank() ? "제목 없음" : title;
        String safeSubject = subject == null || subject.isBlank() ? "과목 없음" : subject;
        return safeTitle + " - " + safeSubject;
    }
}
