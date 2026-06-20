package com.lecturemate.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Set;

/**
 * Validates audio files before they are attached to a lecture.
 */
public final class FileValidator {

    public static final long MAX_AUDIO_FILE_SIZE_BYTES = 25L * 1024L * 1024L;

    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = Set.of("mp3", "wav", "m4a");

    private FileValidator() {
    }

    /**
     * Checks that the file exists, has an allowed extension, and is no larger than 25MB.
     */
    public static ValidationResult validateAudioFile(File file) {
        if (file == null) {
            return ValidationResult.invalid("파일이 선택되지 않았습니다.");
        }

        if (!Files.isRegularFile(file.toPath())) {
            return ValidationResult.invalid("선택한 경로가 올바른 파일이 아닙니다.");
        }

        if (!hasAllowedAudioExtension(file)) {
            return ValidationResult.invalid("mp3, wav, m4a 파일만 선택할 수 있습니다.");
        }

        try {
            if (!isWithinAudioSizeLimit(file)) {
                return ValidationResult.invalid("파일 크기가 25MB를 초과합니다. 현재 크기: "
                        + formatFileSize(Files.size(file.toPath())));
            }
        } catch (IOException exception) {
            return ValidationResult.invalid("파일 크기를 확인할 수 없습니다: " + exception.getMessage());
        }

        return ValidationResult.success();
    }

    /**
     * Returns true when the file extension is one of mp3, wav, or m4a.
     */
    public static boolean hasAllowedAudioExtension(File file) {
        return ALLOWED_AUDIO_EXTENSIONS.contains(getExtension(file));
    }

    /**
     * Returns true when the file size is at or below the 25MB limit.
     */
    public static boolean isWithinAudioSizeLimit(File file) throws IOException {
        return Files.size(file.toPath()) <= MAX_AUDIO_FILE_SIZE_BYTES;
    }

    /**
     * Formats byte counts for UI messages.
     */
    public static String formatFileSize(long bytes) {
        double megabytes = bytes / 1024.0 / 1024.0;
        return String.format(Locale.US, "%.2f MB", megabytes);
    }

    private static String getExtension(File file) {
        String fileName = file == null ? "" : file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Lightweight validation response for UI-friendly error handling.
     */
    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult success() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }
}
