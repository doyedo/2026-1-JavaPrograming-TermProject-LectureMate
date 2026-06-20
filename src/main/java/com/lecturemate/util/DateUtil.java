package com.lecturemate.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Small date/time helper for SQLite text persistence and JavaFX display.
 */
public final class DateUtil {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateUtil() {
    }

    public static String nowDateTimeString() {
        return LocalDateTime.now().toString();
    }

    public static String toDateString(LocalDate value) {
        return value == null ? LocalDate.now().toString() : value.toString();
    }

    public static String toTimeString(LocalTime value) {
        return value == null ? LocalTime.of(9, 0).toString() : value.toString();
    }

    public static String toDateTimeString(LocalDateTime value) {
        return value == null ? LocalDateTime.now().toString() : value.toString();
    }

    public static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    public static LocalTime parseTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    public static LocalDateTime parseDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    public static String displayDate(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    public static String displayTime(LocalTime value) {
        return value == null ? "-" : value.format(TIME);
    }

    public static String displayDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME);
    }
}
