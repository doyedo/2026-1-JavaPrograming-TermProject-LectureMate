package com.lecturemate.service;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Records microphone input to a temporary WAV file that can be reused by STT.
 */
public class AudioRecordingService {

    private static final AudioFormat RECORDING_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16_000.0f,
            16,
            1,
            2,
            16_000.0f,
            false);

    private TargetDataLine microphoneLine;
    private Thread recordingThread;
    private Path recordingPath;
    private ByteArrayOutputStream audioBuffer;
    private volatile boolean recording;
    private volatile boolean paused;
    private volatile RuntimeException recordingFailure;

    /**
     * Starts microphone capture and prepares a temporary WAV file path.
     */
    public synchronized File startRecording() {
        if (recording) {
            throw new IllegalStateException("이미 녹음 중입니다.");
        }

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, RECORDING_FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IllegalStateException("현재 시스템에서 마이크 녹음을 지원하지 않습니다.");
        }

        try {
            recordingPath = Files.createTempFile("lecturemate-recording-", ".wav");
            microphoneLine = (TargetDataLine) AudioSystem.getLine(info);
            microphoneLine.open(RECORDING_FORMAT);
            microphoneLine.start();
            audioBuffer = new ByteArrayOutputStream();
            recording = true;
            paused = false;
            recordingFailure = null;

            recordingThread = new Thread(this::captureMicrophoneStream, "lecturemate-audio-recorder");
            recordingThread.setDaemon(true);
            recordingThread.start();
            return recordingPath.toFile();
        } catch (LineUnavailableException | IOException exception) {
            cleanupFailedRecording();
            throw new IllegalStateException("마이크 녹음을 시작할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    /**
     * Temporarily stops adding microphone input to the recording buffer.
     */
    public synchronized void pauseRecording() {
        if (!recording || microphoneLine == null) {
            throw new IllegalStateException("진행 중인 녹음이 없습니다.");
        }
        if (paused) {
            return;
        }
        paused = true;
        microphoneLine.stop();
    }

    /**
     * Resumes microphone capture after a pause.
     */
    public synchronized void resumeRecording() {
        if (!recording || microphoneLine == null) {
            throw new IllegalStateException("진행 중인 녹음이 없습니다.");
        }
        if (!paused) {
            return;
        }
        microphoneLine.start();
        paused = false;
    }

    /**
     * Stops capture and returns the WAV file written during the recording session.
     */
    public synchronized File stopRecording() {
        if (!recording || microphoneLine == null || recordingPath == null) {
            throw new IllegalStateException("진행 중인 녹음이 없습니다.");
        }

        recording = false;
        paused = false;
        microphoneLine.stop();
        microphoneLine.close();

        if (recordingThread != null) {
            try {
                recordingThread.join(1500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("녹음 종료 중 인터럽트가 발생했습니다.", exception);
            }
        }

        if (recordingFailure != null) {
            cleanupFailedRecording();
            throw recordingFailure;
        }

        File recordedFile = recordingPath.toFile();
        writeWavFile(recordedFile);
        microphoneLine = null;
        recordingThread = null;
        recordingPath = null;
        audioBuffer = null;
        return recordedFile;
    }

    public boolean isRecording() {
        return recording;
    }

    public boolean isPaused() {
        return paused;
    }

    private void captureMicrophoneStream() {
        byte[] buffer = new byte[Math.max(4096, microphoneLine.getBufferSize() / 5)];
        try {
            while (recording) {
                if (paused) {
                    sleepQuietly();
                    continue;
                }
                int bytesRead = microphoneLine.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && !paused) {
                    synchronized (this) {
                        audioBuffer.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (RuntimeException exception) {
            recording = false;
            recordingFailure = new IllegalStateException("녹음 데이터를 읽는 중 오류가 발생했습니다: "
                    + exception.getMessage(), exception);
        }
    }

    private synchronized void writeWavFile(File outputFile) {
        byte[] recordedBytes = audioBuffer == null ? new byte[0] : audioBuffer.toByteArray();
        long frameLength = recordedBytes.length / RECORDING_FORMAT.getFrameSize();
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(recordedBytes);
                AudioInputStream audioStream = new AudioInputStream(byteStream, RECORDING_FORMAT, frameLength)) {
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile);
        } catch (IOException exception) {
            throw new IllegalStateException("녹음 파일을 저장할 수 없습니다: " + exception.getMessage(), exception);
        }
    }

    private void cleanupFailedRecording() {
        recording = false;
        if (microphoneLine != null) {
            microphoneLine.close();
            microphoneLine = null;
        }
        if (recordingPath != null) {
            try {
                Files.deleteIfExists(recordingPath);
            } catch (IOException ignored) {
                // Temporary files are best-effort cleanup only.
            }
            recordingPath = null;
        }
        audioBuffer = null;
        recordingThread = null;
        recordingFailure = null;
        paused = false;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recording = false;
        }
    }
}
