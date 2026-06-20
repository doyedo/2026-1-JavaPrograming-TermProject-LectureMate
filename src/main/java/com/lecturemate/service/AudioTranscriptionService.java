package com.lecturemate.service;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecturemate.util.ApiConfig;
import com.lecturemate.util.OpenAiErrorUtil;

/**
 * Uploads lecture audio to the OpenAI Audio Transcriptions API.
 * Missing keys and failed requests fall back to a sample transcript.
 */
public class AudioTranscriptionService {

    private static final int REQUEST_TIMEOUT_SECONDS = 120;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AudioTranscriptionService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), new ObjectMapper());
    }

    AudioTranscriptionService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Transcribes a file using the selected language and model options.
     */
    public TranscriptionResult transcribeWithResult(File audioFile, TranscriptionOptions options) {
        TranscriptionOptions safeOptions = options == null ? TranscriptionOptions.defaults() : options.normalized();
        if (!ApiConfig.hasOpenAiApiKey()) {
            return fallback("OPENAI_API_KEY가 없어 샘플 원문을 표시합니다.");
        }
        if (audioFile == null || !Files.isRegularFile(audioFile.toPath())) {
            return fallback("변환할 녹음 파일을 찾을 수 없어 샘플 원문을 표시합니다.");
        }

        try {
            HttpRequest request = buildRequest(audioFile, safeOptions);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return fallback(OpenAiErrorUtil.fallbackMessage("샘플 원문", response.statusCode(), response.body()));
            }

            String text = extractText(response.body());
            if (text.isBlank()) {
                return fallback("OpenAI 응답에서 텍스트를 찾을 수 없어 샘플 원문을 표시합니다.");
            }
            return new TranscriptionResult(text, false, "텍스트 변환 완료");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallback("텍스트 변환이 중단되어 샘플 원문을 표시합니다.");
        } catch (IOException | RuntimeException exception) {
            return fallback("OpenAI API 호출 중 오류가 발생했습니다: " + exception.getMessage());
        }
    }

    private HttpRequest buildRequest(File audioFile, TranscriptionOptions options) throws IOException {
        String boundary = "----LectureMateBoundary" + UUID.randomUUID();
        HttpRequest.BodyPublisher body = buildMultipartBody(audioFile, boundary, options);
        return HttpRequest.newBuilder(ApiConfig.AUDIO_TRANSCRIPTIONS_URI)
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Authorization", "Bearer " + ApiConfig.getOpenAiApiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(body)
                .build();
    }

    private HttpRequest.BodyPublisher buildMultipartBody(
            File audioFile,
            String boundary,
            TranscriptionOptions options) throws IOException {
        List<HttpRequest.BodyPublisher> publishers = new ArrayList<>();
        addTextPart(publishers, boundary, "model", options.modelName());
        addTextPart(publishers, boundary, "response_format", "json");
        if (options.hasLanguage()) {
            addTextPart(publishers, boundary, "language", options.languageCode());
        }
        addFilePart(publishers, boundary, "file", audioFile);
        publishers.add(BodyPublishers.ofString("--" + boundary + "--\r\n", StandardCharsets.UTF_8));
        return BodyPublishers.concat(publishers.toArray(new HttpRequest.BodyPublisher[0]));
    }

    private void addTextPart(List<HttpRequest.BodyPublisher> publishers, String boundary, String name, String value) {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        publishers.add(BodyPublishers.ofString(part, StandardCharsets.UTF_8));
    }

    private void addFilePart(List<HttpRequest.BodyPublisher> publishers, String boundary, String name, File file)
            throws IOException {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                + file.getName().replace("\"", "_") + "\"\r\n"
                + "Content-Type: " + detectContentType(file) + "\r\n\r\n";
        publishers.add(BodyPublishers.ofString(header, StandardCharsets.UTF_8));
        publishers.add(BodyPublishers.ofFile(file.toPath()));
        publishers.add(BodyPublishers.ofString("\r\n", StandardCharsets.UTF_8));
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode textNode = root.get("text");
        return textNode == null || textNode.isNull() ? "" : textNode.asText("");
    }

    private String detectContentType(File file) throws IOException {
        String detected = Files.probeContentType(file.toPath());
        if (detected != null && !detected.isBlank()) {
            return detected;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (name.endsWith(".wav")) {
            return "audio/wav";
        }
        if (name.endsWith(".m4a")) {
            return "audio/mp4";
        }
        return "application/octet-stream";
    }

    private TranscriptionResult fallback(String message) {
        return new TranscriptionResult("""
                [샘플 강의 원문]
                오늘 강의에서는 운영체제의 프로세스와 스레드 개념을 살펴보겠습니다.
                프로세스는 실행 중인 프로그램의 단위이며 독립적인 메모리 공간을 가집니다.
                스레드는 프로세스 내부에서 실행되는 작업 흐름으로, 같은 프로세스의 자원을 공유합니다.
                문맥 교환은 CPU가 실행 중인 작업을 바꿀 때 필요한 상태 저장과 복원 과정입니다.
                병렬성과 동시성의 차이를 이해하고, 공유 자원 접근 시 동기화가 필요한 이유를 설명할 수 있어야 합니다.
                다음 시간에는 뮤텍스와 세마포어를 사용해 임계 구역 문제를 해결하는 방법을 다룰 예정입니다.
                """, true, message);
    }

    public record TranscriptionResult(String text, boolean fallbackUsed, String message) {
    }

    /**
     * STT request options selected in the conversion settings panel.
     */
    public record TranscriptionOptions(String languageCode, String modelName) {
        public static TranscriptionOptions defaults() {
            return new TranscriptionOptions("", ApiConfig.DEFAULT_TRANSCRIPTION_MODEL);
        }

        public TranscriptionOptions normalized() {
            String safeModel = modelName == null || modelName.isBlank()
                    ? ApiConfig.DEFAULT_TRANSCRIPTION_MODEL
                    : modelName.trim();
            return new TranscriptionOptions(languageCode == null ? "" : languageCode.trim(), safeModel);
        }

        public boolean hasLanguage() {
            return languageCode != null && !languageCode.isBlank();
        }
    }
}
