package com.lecturemate.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecturemate.util.ApiConfig;
import com.lecturemate.util.OpenAiErrorUtil;

/**
 * Generates editable lecture summaries through OpenAI, with a local fallback for demos.
 */
public class LectureSummaryService {

    private static final int REQUEST_TIMEOUT_SECONDS = 90;
    private static final int MAX_TRANSCRIPT_CHARS = 18_000;
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?。！？])\\s+|\\R+");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "그리고", "그러나", "입니다", "합니다", "있는", "없는", "the", "and", "for", "that");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LectureSummaryService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), new ObjectMapper());
    }

    LectureSummaryService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a summary with the selected format. Failed API calls return a deterministic fallback.
     */
    public SummaryResult generateSummaryWithResult(String transcript, String summaryFormat) {
        String safeTranscript = transcript == null ? "" : transcript.trim();
        String safeFormat = summaryFormat == null || summaryFormat.isBlank() ? "기본 강의 요약" : summaryFormat;
        if (safeTranscript.isBlank()) {
            return fallback("원문 텍스트가 없어 샘플 요약을 표시합니다.", safeTranscript, safeFormat);
        }
        if (!ApiConfig.hasOpenAiApiKey()) {
            return fallback("OPENAI_API_KEY가 없어 샘플 요약을 표시합니다.", safeTranscript, safeFormat);
        }

        try {
            HttpRequest request = buildRequest(safeTranscript, safeFormat);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback(
                        OpenAiErrorUtil.fallbackMessage("샘플 요약", response.statusCode(), response.body()),
                        safeTranscript,
                        safeFormat);
            }

            String text = extractResponseText(response.body());
            if (text.isBlank()) {
                return fallback("OpenAI 응답에서 요약을 찾을 수 없어 샘플 요약을 표시합니다.", safeTranscript, safeFormat);
            }
            return new SummaryResult(text, false, "요약 생성 완료");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallback("요약 생성이 중단되어 샘플 요약을 표시합니다.", safeTranscript, safeFormat);
        } catch (IOException | RuntimeException exception) {
            return fallback("OpenAI 요약 API 호출 중 오류가 발생했습니다: " + exception.getMessage(),
                    safeTranscript,
                    safeFormat);
        }
    }

    private HttpRequest buildRequest(String transcript, String summaryFormat) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ApiConfig.getSummaryModel());
        body.put("instructions", buildInstructions(summaryFormat));
        body.put("input", clipTranscript(transcript));

        String jsonBody = objectMapper.writeValueAsString(body);
        return HttpRequest.newBuilder(ApiConfig.RESPONSES_URI)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Authorization", "Bearer " + ApiConfig.getOpenAiApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private String buildInstructions(String summaryFormat) {
        return """
                You are LectureMate, a Korean study note assistant.
                Write in Korean. Create concise but practical notes from a lecture transcript.
                Use the selected format exactly and keep the output easy to edit.

                Selected format: %s
                Required structure:
                %s
                """.formatted(summaryFormat, structureFor(summaryFormat));
    }

    private String structureFor(String summaryFormat) {
        return switch (summaryFormat) {
            case "시험 대비 요약" -> """
                    1. 시험에 나올 만한 개념
                    2. 암기 포인트
                    3. 비교 정리
                    4. 예상 문제
                    5. 주의해야 할 개념
                    """;
            case "키워드 중심 요약" -> """
                    1. 핵심 키워드
                    2. 키워드별 설명
                    3. 관련 개념
                    4. 중요도
                    """;
            case "복습 질문 포함 요약" -> """
                    1. 전체 요약
                    2. 단답형 질문
                    3. 서술형 질문
                    4. 빈칸 문제
                    5. 답안 예시
                    """;
            default -> """
                    1. 전체 요약
                    2. 핵심 개념
                    3. 중요 키워드
                    4. 시험에 나올 만한 내용
                    5. 복습 질문 5개
                    6. 일정 후보
                    """;
        };
    }

    private String clipTranscript(String transcript) {
        if (transcript.length() <= MAX_TRANSCRIPT_CHARS) {
            return transcript;
        }
        return transcript.substring(0, MAX_TRANSCRIPT_CHARS)
                + "\n\n[원문 일부가 길이 제한으로 생략되었습니다.]";
    }

    private String extractResponseText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputText = root.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText("");
        }

        StringBuilder builder = new StringBuilder();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    JsonNode text = contentItem.get("text");
                    if (text != null && text.isTextual()) {
                        if (!builder.isEmpty()) {
                            builder.append(System.lineSeparator());
                        }
                        builder.append(text.asText());
                    }
                }
            }
        }
        return builder.toString().trim();
    }

    private SummaryResult fallback(String message, String transcript, String summaryFormat) {
        return new SummaryResult(buildFallbackSummary(transcript, summaryFormat), true, message);
    }

    private String buildFallbackSummary(String transcript, String summaryFormat) {
        List<String> sentences = splitSentences(transcript);
        List<String> keywords = extractKeywords(transcript);
        String keywordLine = keywords.isEmpty() ? "핵심 개념, 예시, 복습" : String.join(", ", keywords);
        String overview = sentences.stream().limit(3).collect(Collectors.joining(" "));
        if (overview.isBlank()) {
            overview = "강의 원문을 바탕으로 핵심 개념과 복습 포인트를 정리합니다.";
        }

        return switch (summaryFormat) {
            case "시험 대비 요약" -> """
                    1. 시험에 나올 만한 개념
                    - %s

                    2. 암기 포인트
                    - 핵심 키워드: %s
                    - 정의, 특징, 예시를 함께 암기합니다.

                    3. 비교 정리
                    - 서로 비슷한 개념은 기준을 세워 차이를 정리합니다.

                    4. 예상 문제
                    - 핵심 개념을 설명하고 실제 상황에 적용하는 문제가 나올 수 있습니다.

                    5. 주의해야 할 개념
                    - 용어의 정의와 적용 조건을 혼동하지 않도록 복습합니다.
                    """.formatted(overview, keywordLine);
            case "키워드 중심 요약" -> """
                    1. 핵심 키워드
                    - %s

                    2. 키워드별 설명
                    - 각 키워드의 정의와 강의에서 언급된 예시를 연결해 정리합니다.

                    3. 관련 개념
                    - 원문에서 함께 등장하는 개념들을 묶어 복습합니다.

                    4. 중요도
                    - 반복적으로 언급된 키워드를 우선 복습하세요.
                    """.formatted(keywordLine);
            case "복습 질문 포함 요약" -> """
                    1. 전체 요약
                    %s

                    2. 단답형 질문
                    - 핵심 키워드 중 하나를 정의해 보세요.

                    3. 서술형 질문
                    - 오늘 배운 개념이 실제 문제 해결에 어떻게 쓰이는지 설명하세요.

                    4. 빈칸 문제
                    - ______ 은/는 강의에서 반복적으로 등장한 핵심 개념입니다.

                    5. 답안 예시
                    - 답안에는 정의, 특징, 예시가 포함되어야 합니다.
                    """.formatted(overview);
            default -> """
                    1. 전체 요약
                    %s

                    2. 핵심 개념
                    - 원문에서 반복되는 개념과 정의를 중심으로 정리합니다.

                    3. 중요 키워드
                    - %s

                    4. 시험에 나올 만한 내용
                    - 핵심 용어의 정의, 비교, 적용 예시를 확인하세요.

                    5. 복습 질문 5개
                    - 핵심 개념을 한 문장으로 설명할 수 있나요?
                    - 관련 예시를 들어 설명할 수 있나요?
                    - 비슷한 개념과의 차이를 말할 수 있나요?
                    - 시험 문제로 바뀌면 어떤 형태가 될까요?
                    - 오늘 배운 내용을 다음 강의와 연결할 수 있나요?

                    6. 일정 후보
                    - 오늘: 요약 읽고 직접 필기 보완
                    - 2일 후: 복습 질문 풀기
                    - 1주 후: 시험 대비 키워드 재정리
                    """.formatted(overview, keywordLine);
        };
    }

    private List<String> splitSentences(String transcript) {
        String safeText = transcript == null || transcript.isBlank()
                ? "샘플 강의에서는 핵심 개념, 예시, 시험 대비 포인트를 정리합니다."
                : transcript.strip();
        return Arrays.stream(SENTENCE_SPLIT_PATTERN.split(safeText))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .limit(8)
                .toList();
    }

    private List<String> extractKeywords(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return List.of("강의", "핵심", "복습");
        }
        return Arrays.stream(TOKEN_SPLIT_PATTERN.split(transcript.toLowerCase(Locale.ROOT)))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.groupingBy(token -> token, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    public record SummaryResult(String text, boolean fallbackUsed, String message) {
    }
}
