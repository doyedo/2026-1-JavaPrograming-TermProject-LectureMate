package com.lecturemate.util;

/**
 * Converts OpenAI HTTP error responses into concise Korean UI messages.
 */
public final class OpenAiErrorUtil {

    private OpenAiErrorUtil() {
    }

    public static String fallbackMessage(String fallbackTarget, int statusCode, String responseBody) {
        String target = fallbackTarget == null || fallbackTarget.isBlank() ? "샘플 결과" : fallbackTarget;
        String body = responseBody == null ? "" : responseBody.toLowerCase();

        if (statusCode == 401) {
            return "OpenAI API Key 인증에 실패했습니다. 환경 변수 OPENAI_API_KEY를 확인해 주세요. " + target + "을 표시합니다.";
        }
        if (statusCode == 403) {
            return "현재 API Key에 요청한 모델 또는 API 사용 권한이 없습니다. " + target + "을 표시합니다.";
        }
        if (statusCode == 429) {
            if (body.contains("insufficient_quota")
                    || body.contains("current quota")
                    || body.contains("billing")) {
                return "OpenAI 사용량 한도 또는 결제 크레딧이 부족합니다. " + target + "을 표시합니다.";
            }
            return "OpenAI 요청 제한에 걸렸습니다. 잠시 후 다시 시도해 주세요. " + target + "을 표시합니다.";
        }
        if (statusCode >= 500) {
            return "OpenAI 서버 응답이 불안정합니다(" + statusCode + "). " + target + "을 표시합니다.";
        }
        return "OpenAI API 호출에 실패했습니다(" + statusCode + "). " + target + "을 표시합니다.";
    }
}
