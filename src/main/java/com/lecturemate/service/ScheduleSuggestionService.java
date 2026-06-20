package com.lecturemate.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts simple review schedule hints from generated summaries.
 * The current UI lets the user add schedules manually, but this service keeps
 * schedule-candidate logic separated for future automation.
 */
public class ScheduleSuggestionService {

    /**
     * Returns lightweight schedule suggestions when a summary mentions schedule candidates.
     */
    public List<String> suggestFromSummary(String summary) {
        List<String> suggestions = new ArrayList<>();
        if (summary == null || summary.isBlank()) {
            suggestions.add("오늘: 정리 노트 읽고 원문 보완");
            suggestions.add("2일 후: 복습 질문 풀기");
            suggestions.add("1주 후: 핵심 키워드 재정리");
            return suggestions;
        }

        for (String line : summary.split("\\R+")) {
            String trimmed = line.trim();
            if (trimmed.contains("오늘") || trimmed.contains("후") || trimmed.contains("복습")) {
                suggestions.add(trimmed.replaceFirst("^-\\s*", ""));
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("오늘: 정리 노트 읽고 중요 개념 표시");
            suggestions.add("3일 후: 핵심 키워드 복습");
        }
        return suggestions;
    }
}
