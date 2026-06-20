package com.lecturemate.util;

import java.net.URI;

/**
 * Centralizes OpenAI API configuration without storing secrets in source code.
 */
public final class ApiConfig {

    public static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";
    public static final String OPENAI_SUMMARY_MODEL_ENV = "OPENAI_SUMMARY_MODEL";
    public static final String DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe";
    public static final String DEFAULT_SUMMARY_MODEL = "gpt-4.1-mini";
    public static final URI AUDIO_TRANSCRIPTIONS_URI = URI.create("https://api.openai.com/v1/audio/transcriptions");
    public static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");

    private ApiConfig() {
    }

    /**
     * Reads the OpenAI API key from the OPENAI_API_KEY environment variable.
     */
    public static String getOpenAiApiKey() {
        String apiKey = System.getenv(OPENAI_API_KEY_ENV);
        return apiKey == null ? "" : apiKey.trim();
    }

    public static boolean hasOpenAiApiKey() {
        return !getOpenAiApiKey().isBlank();
    }

    /**
     * Allows the text generation model to be overridden without changing source code.
     */
    public static String getSummaryModel() {
        String model = System.getenv(OPENAI_SUMMARY_MODEL_ENV);
        return model == null || model.isBlank() ? DEFAULT_SUMMARY_MODEL : model.trim();
    }
}
