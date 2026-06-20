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
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecturemate.util.ApiConfig;
import com.lecturemate.util.OpenAiErrorUtil;

/**
 * Answers user questions from the currently opened lecture note context.
 */
public class LectureQuestionService {

    private static final int REQUEST_TIMEOUT_SECONDS = 60;
    private static final int MAX_CONTEXT_CHARS = 14_000;
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?。！？])\\s+|\\R+");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}가-힣]+");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LectureQuestionService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), new ObjectMapper());
    }

    LectureQuestionService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Uses OpenAI when available; otherwise returns a deterministic local answer.
     */
    public QuestionResult askWithResult(String noteContext, String question) {
        String safeContext = noteContext == null ? "" : noteContext.trim();
        String safeQuestion = question == null ? "" : question.trim();
        if (safeContext.isBlank()) {
            return new QuestionResult("현재 열려 있는 노트 내용이 없어 답변할 수 없습니다.", true, "노트 내용 없음");
        }
        if (safeQuestion.isBlank()) {
            return new QuestionResult("질문을 입력해 주세요.", true, "질문 없음");
        }
        if (!ApiConfig.hasOpenAiApiKey()) {
            return fallback("OPENAI_API_KEY가 없어 샘플 답변을 표시합니다.", safeContext, safeQuestion);
        }

        try {
            HttpRequest request = buildRequest(safeContext, safeQuestion);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback(
                        OpenAiErrorUtil.fallbackMessage("샘플 답변", response.statusCode(), response.body()),
                        safeContext,
                        safeQuestion);
            }

            String answer = extractResponseText(response.body());
            if (answer.isBlank()) {
                return fallback("OpenAI 응답에서 답변을 찾지 못해 샘플 답변을 표시합니다.", safeContext, safeQuestion);
            }
            return new QuestionResult(answer, false, "질문 답변 완료");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallback("질문 답변이 중단되어 샘플 답변을 표시합니다.", safeContext, safeQuestion);
        } catch (IOException | RuntimeException exception) {
            return fallback("OpenAI 질문 API 호출 중 오류가 발생했습니다: " + exception.getMessage(),
                    safeContext,
                    safeQuestion);
        }
    }

    private HttpRequest buildRequest(String noteContext, String question) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ApiConfig.getSummaryModel());
        body.put("instructions", """
                You are LectureMate's Korean lecture-note Q&A assistant.
                Answer in Korean. Use only the provided note context.
                If the answer is not supported by the note, say that the note does not contain enough information.
                Keep the answer clear and useful for studying.
                """);
        body.put("input", """
                [현재 노트]
                %s

                [질문]
                %s
                """.formatted(clipContext(noteContext), question));

        String jsonBody = objectMapper.writeValueAsString(body);
        return HttpRequest.newBuilder(ApiConfig.RESPONSES_URI)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Authorization", "Bearer " + ApiConfig.getOpenAiApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private String clipContext(String noteContext) {
        if (noteContext.length() <= MAX_CONTEXT_CHARS) {
            return noteContext;
        }
        return noteContext.substring(0, MAX_CONTEXT_CHARS)
                + "\n\n[노트 일부가 길이 제한으로 생략되었습니다.]";
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

    private QuestionResult fallback(String message, String noteContext, String question) {
        return new QuestionResult(buildFallbackAnswer(noteContext, question), true, message);
    }

    private String buildFallbackAnswer(String noteContext, String question) {
        List<String> questionTokens = Arrays.stream(TOKEN_SPLIT_PATTERN.split(question.toLowerCase(Locale.ROOT)))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .toList();

        List<String> relatedSentences = Arrays.stream(SENTENCE_SPLIT_PATTERN.split(noteContext))
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .filter(sentence -> questionTokens.isEmpty()
                        || questionTokens.stream().anyMatch(token -> sentence.toLowerCase(Locale.ROOT).contains(token)))
                .limit(3)
                .toList();

        if (relatedSentences.isEmpty()) {
            relatedSentences = Arrays.stream(SENTENCE_SPLIT_PATTERN.split(noteContext))
                    .map(String::trim)
                    .filter(sentence -> !sentence.isBlank())
                    .limit(3)
                    .toList();
        }

        String evidence = relatedSentences.isEmpty()
                ? "현재 노트에서 직접 연결되는 문장을 찾지 못했습니다."
                : String.join(System.lineSeparator() + "- ", relatedSentences);

        return """
                샘플 답변입니다. API를 사용할 수 있으면 현재 노트 전체를 바탕으로 더 정확히 답변합니다.

                질문과 관련 있어 보이는 노트 내용:
                - %s

                정리:
                위 내용을 기준으로 답을 구성해 보세요. 노트에 근거가 부족하면 원문 텍스트나 정리 노트를 조금 더 보강하는 것이 좋습니다.
                """.formatted(evidence);
    }

    public record QuestionResult(String answer, boolean fallbackUsed, String message) {
    }
}
