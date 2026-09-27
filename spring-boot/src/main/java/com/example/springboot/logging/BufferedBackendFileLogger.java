package com.example.springboot.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.springboot.models.LogLevel;

@Component
public class BufferedBackendFileLogger {

    private static final String DEFAULT_CATEGORY = "general";
    private static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.INFO;
    private static final String FIELD_DELIMITER = " | ";
    private static final DateTimeFormatter LOG_ENTRY_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateTimeFormatter LOG_FILE_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path logDirectory;
    private final String filePrefix;
    private final int bufferSizeThreshold;
    private final long maxFileSizeBytes;

    private final Buffer7 buffer7 = new Buffer7();
    private final Lock flushLock = new ReentrantLock();
    private final AtomicInteger logFileIndex = new AtomicInteger(1);
    private final String sessionTimestamp = OffsetDateTime.now().format(LOG_FILE_TIMESTAMP_FORMATTER);

    private volatile Path currentLogFile;

    public BufferedBackendFileLogger(
        @Value("${backend.file-logging.directory:./logs/backend}") String logDirectory,
        @Value("${backend.file-logging.file-prefix:backend}") String filePrefix,
        @Value("${backend.file-logging.buffer-size-threshold:200}") int bufferSizeThreshold,
        @Value("${backend.file-logging.max-file-size-bytes:10485760}") long maxFileSizeBytes
    ) {
        this.logDirectory = Path.of(Objects.requireNonNull(logDirectory, "logDirectory must not be null"));
        this.filePrefix = Objects.requireNonNull(filePrefix, "filePrefix must not be null");
        this.bufferSizeThreshold = bufferSizeThreshold;
        this.maxFileSizeBytes = maxFileSizeBytes;

        if (bufferSizeThreshold <= 0) {
            throw new IllegalArgumentException("backend.file-logging.buffer-size-threshold must be > 0");
        }
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("backend.file-logging.max-file-size-bytes must be > 0");
        }
    }

    public void log(Class<?> sourceClass, String message) {
        log(sourceClass != null ? sourceClass.getName() : "unknown", DEFAULT_LOG_LEVEL, DEFAULT_CATEGORY, message);
    }

    public void log(String sourceClassName, String message) {
        log(sourceClassName, DEFAULT_LOG_LEVEL, DEFAULT_CATEGORY, message);
    }

    public void log(Class<?> sourceClass, LogLevel logLevel, String category, String message) {
        log(sourceClass != null ? sourceClass.getName() : "unknown", logLevel, category, message);
    }

    public void log(String sourceClassName, LogLevel logLevel, String category, String message) {
        String timestamp = OffsetDateTime.now().format(LOG_ENTRY_TIMESTAMP_FORMATTER);
        String source = sanitizeField(sourceClassName, "unknown");
        String level = logLevel == null ? DEFAULT_LOG_LEVEL.name() : logLevel.name();
        String normalizedCategory = sanitizeField(category, DEFAULT_CATEGORY);
        String text = message == null ? "" : message.replace("\r", "\\r").replace("\n", "\\n");
        String logLine = timestamp
            + FIELD_DELIMITER + level
            + FIELD_DELIMITER + normalizedCategory
            + FIELD_DELIMITER + source
            + FIELD_DELIMITER + text;

        int bufferSize = buffer7.add(logLine);
        if (bufferSize >= bufferSizeThreshold) {
            flushBufferedLogs();
        }
    }

    @Scheduled(fixedDelayString = "${backend.file-logging.flush-interval-ms:10000}")
    public void flushIfBufferedLogsExist() {
        if (!buffer7.isEmpty()) {
            flushBufferedLogs();
        }
    }

    @PreDestroy
    public void flushRemainingLogsOnShutdown() {
        if (!buffer7.isEmpty()) {
            flushBufferedLogs();
        }
    }

    private void flushBufferedLogs() {
        if (!flushLock.tryLock()) {
            return;
        }

        try {
            List<String> entriesToWrite = buffer7.drainAll();
            if (entriesToWrite.isEmpty()) {
                return;
            }
            writeEntries(entriesToWrite);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write buffered backend logs", ex);
        } finally {
            flushLock.unlock();
        }
    }

    private void writeEntries(List<String> entriesToWrite) throws IOException {
        ensureLogDirectoryAndActiveFile();

        BufferedWriter writer = openWriter(currentLogFile);
        try {
            long currentFileSize = Files.exists(currentLogFile) ? Files.size(currentLogFile) : 0L;
            for (String entry : entriesToWrite) {
                byte[] lineBytes = (entry + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                if (currentFileSize + lineBytes.length > maxFileSizeBytes) {
                    writer.close();
                    rotateToNextFile();
                    writer = openWriter(currentLogFile);
                    currentFileSize = Files.exists(currentLogFile) ? Files.size(currentLogFile) : 0L;
                }

                writer.write(entry);
                writer.newLine();
                currentFileSize += lineBytes.length;
            }
            writer.flush();
        } finally {
            writer.close();
        }
    }

    private void ensureLogDirectoryAndActiveFile() throws IOException {
        Files.createDirectories(logDirectory);
        if (currentLogFile == null) {
            currentLogFile = resolveLogFilePath(logFileIndex.get());
        }
    }

    private void rotateToNextFile() {
        int nextFileIndex = logFileIndex.incrementAndGet();
        currentLogFile = resolveLogFilePath(nextFileIndex);
    }

    private Path resolveLogFilePath(int fileIndex) {
        return logDirectory.resolve(filePrefix + "-" + sessionTimestamp + "-" + fileIndex + ".log");
    }

    private static BufferedWriter openWriter(Path filePath) throws IOException {
        return Files.newBufferedWriter(
            filePath,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
    }

    private static String sanitizeField(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(FIELD_DELIMITER, " / ")
            .trim();
    }
}
