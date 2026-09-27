package com.example.springboot.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.springboot.models.BackendFileLogEntry;
import com.example.springboot.models.BackendFileLogResponse;
import com.example.springboot.models.BackendFileLogStorageResponse;

@Service
public class BackendFileLogQueryService {

    private static final String DEFAULT_LOG_LEVEL = "INFO";
    private static final String DEFAULT_CATEGORY = "general";
    private static final DateTimeFormatter LOG_ENTRY_TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final Path logDirectory;
    private final String filePrefix;
    private final int queryMaxResults;

    public BackendFileLogQueryService(
        @Value("${backend.file-logging.directory:./logs/backend}") String logDirectory,
        @Value("${backend.file-logging.file-prefix:backend}") String filePrefix,
        @Value("${backend.file-logging.query-max-results:1000}") int queryMaxResults
    ) {
        this.logDirectory = Path.of(Objects.requireNonNull(logDirectory, "logDirectory must not be null"));
        this.filePrefix = Objects.requireNonNull(filePrefix, "filePrefix must not be null");
        this.queryMaxResults = queryMaxResults;
        if (queryMaxResults <= 0) {
            throw new IllegalArgumentException("backend.file-logging.query-max-results must be > 0");
        }
    }

    public BackendFileLogResponse getLogsByTimeWindow(
        Instant fromInclusive,
        Instant toInclusive,
        String sourceClassFilter,
        String logLevelFilter,
        String categoryFilter,
        Integer limit
    ) {
        if (fromInclusive != null && toInclusive != null && fromInclusive.isAfter(toInclusive)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        int effectiveLimit = normalizeWindowLimit(limit);
        String normalizedSourceClassFilter = normalizeContainsFilter(sourceClassFilter);
        String normalizedLogLevelFilter = normalizeEqualsFilter(logLevelFilter);
        String normalizedCategoryFilter = normalizeContainsFilter(categoryFilter);

        List<Path> files = listLogFiles();
        List<BackendFileLogEntry> reverseChronologicalMatches = new ArrayList<>(Math.min(effectiveLimit, 512));
        for (int fileIndex = files.size() - 1; fileIndex >= 0 && reverseChronologicalMatches.size() < effectiveLimit; fileIndex--) {
            List<String> lines = readRawLines(files.get(fileIndex));
            for (int lineIndex = lines.size() - 1; lineIndex >= 0 && reverseChronologicalMatches.size() < effectiveLimit; lineIndex--) {
                Optional<BackendFileLogEntry> parsed = parseEntryLine(lines.get(lineIndex));
                if (parsed.isEmpty()) {
                    continue;
                }

                BackendFileLogEntry entry = parsed.get();
                if (!isWithinWindow(entry.timestamp(), fromInclusive, toInclusive)) {
                    continue;
                }
                if (!matchesFilters(
                    entry,
                    normalizedSourceClassFilter,
                    normalizedLogLevelFilter,
                    normalizedCategoryFilter
                )) {
                    continue;
                }
                reverseChronologicalMatches.add(entry);
            }
        }

        Collections.reverse(reverseChronologicalMatches);
        BackendFileLogStorageResponse storage = getStorageUsage();
        return new BackendFileLogResponse(
            reverseChronologicalMatches,
            reverseChronologicalMatches.size(),
            storage.totalBytes(),
            storage.totalSizeDisplay(),
            storage.fileCount()
        );
    }

    public BackendFileLogResponse getLastLogs(
        int count,
        String sourceClassFilter,
        String logLevelFilter,
        String categoryFilter
    ) {
        if (count <= 0) {
            throw new IllegalArgumentException("'count' must be greater than 0");
        }

        int effectiveCount = Math.min(count, queryMaxResults);
        String normalizedSourceClassFilter = normalizeContainsFilter(sourceClassFilter);
        String normalizedLogLevelFilter = normalizeEqualsFilter(logLevelFilter);
        String normalizedCategoryFilter = normalizeContainsFilter(categoryFilter);

        List<Path> files = listLogFiles();
        List<BackendFileLogEntry> reverseChronologicalMatches = new ArrayList<>(Math.min(effectiveCount, 512));
        for (int fileIndex = files.size() - 1; fileIndex >= 0 && reverseChronologicalMatches.size() < effectiveCount; fileIndex--) {
            List<String> lines = readRawLines(files.get(fileIndex));
            for (int lineIndex = lines.size() - 1; lineIndex >= 0 && reverseChronologicalMatches.size() < effectiveCount; lineIndex--) {
                Optional<BackendFileLogEntry> parsed = parseEntryLine(lines.get(lineIndex));
                if (parsed.isEmpty()) {
                    continue;
                }
                BackendFileLogEntry entry = parsed.get();
                if (!matchesFilters(
                    entry,
                    normalizedSourceClassFilter,
                    normalizedLogLevelFilter,
                    normalizedCategoryFilter
                )) {
                    continue;
                }
                reverseChronologicalMatches.add(entry);
            }
        }

        Collections.reverse(reverseChronologicalMatches);
        BackendFileLogStorageResponse storage = getStorageUsage();
        return new BackendFileLogResponse(
            reverseChronologicalMatches,
            reverseChronologicalMatches.size(),
            storage.totalBytes(),
            storage.totalSizeDisplay(),
            storage.fileCount()
        );
    }

    public BackendFileLogStorageResponse getStorageUsage() {
        List<Path> files = listLogFiles();
        long totalBytes = files.stream()
            .mapToLong(this::sizeOf)
            .sum();
        return new BackendFileLogStorageResponse(totalBytes, formatBytes(totalBytes), files.size());
    }

    private List<Path> listLogFiles() {
        if (!Files.exists(logDirectory)) {
            return List.of();
        }

        try (var stream = Files.list(logDirectory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isManagedLogFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list backend log files", ex);
        }
    }

    private boolean isManagedLogFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        return fileName.startsWith(filePrefix + "-") && fileName.endsWith(".log");
    }

    private List<String> readRawLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read backend log file: " + file, ex);
        }
    }

    private Optional<BackendFileLogEntry> parseEntryLine(String rawLine) {
        String[] parts = rawLine.split("\\s\\|\\s", 5);
        if (parts.length == 5 && isKnownLogLevel(parts[1])) {
            try {
                Instant timestamp = OffsetDateTime.parse(parts[0], LOG_ENTRY_TIMESTAMP_FORMATTER).toInstant();
                String logLevel = sanitizeValueOrDefault(parts[1], DEFAULT_LOG_LEVEL);
                String category = sanitizeValueOrDefault(parts[2], DEFAULT_CATEGORY);
                String sourceClass = sanitizeValueOrDefault(parts[3], "unknown");
                String message = parts[4];
                return Optional.of(new BackendFileLogEntry(timestamp, sourceClass, logLevel, category, message));
            } catch (DateTimeParseException ex) {
                return Optional.empty();
            }
        }

        if (parts.length >= 3) {
            try {
                Instant timestamp = OffsetDateTime.parse(parts[0], LOG_ENTRY_TIMESTAMP_FORMATTER).toInstant();
                String sourceClass = sanitizeValueOrDefault(parts[1], "unknown");
                String message = String.join(" | ", List.of(parts).subList(2, parts.length));
                return Optional.of(new BackendFileLogEntry(timestamp, sourceClass, DEFAULT_LOG_LEVEL, DEFAULT_CATEGORY, message));
            } catch (DateTimeParseException ex) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static String sanitizeValueOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static boolean isKnownLogLevel(String value) {
        String normalized = normalizeEqualsFilter(value);
        if (normalized == null) {
            return false;
        }
        return normalized.equals("DEBUG")
            || normalized.equals("INFO")
            || normalized.equals("WARN")
            || normalized.equals("ERROR");
    }

    private static String normalizeContainsFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeEqualsFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizeWindowLimit(Integer limit) {
        if (limit == null) {
            return queryMaxResults;
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("'limit' must be greater than 0");
        }
        return Math.min(limit, queryMaxResults);
    }

    private static boolean matchesFilters(
        BackendFileLogEntry entry,
        String sourceClassFilter,
        String logLevelFilter,
        String categoryFilter
    ) {
        String sourceClass = entry.sourceClass() == null ? "" : entry.sourceClass().toLowerCase(Locale.ROOT);
        String logLevel = entry.logLevel() == null ? "" : entry.logLevel().toUpperCase(Locale.ROOT);
        String category = entry.category() == null ? "" : entry.category().toLowerCase(Locale.ROOT);

        if (sourceClassFilter != null && !sourceClass.contains(sourceClassFilter)) {
            return false;
        }
        if (logLevelFilter != null && !logLevel.equals(logLevelFilter)) {
            return false;
        }
        if (categoryFilter != null && !category.contains(categoryFilter)) {
            return false;
        }
        return true;
    }

    private static boolean isWithinWindow(Instant timestamp, Instant fromInclusive, Instant toInclusive) {
        if (fromInclusive != null && timestamp.isBefore(fromInclusive)) {
            return false;
        }
        if (toInclusive != null && timestamp.isAfter(toInclusive)) {
            return false;
        }
        return true;
    }

    private long sizeOf(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read log file size for " + filePath, ex);
        }
    }

    private static String formatBytes(long totalBytes) {
        if (totalBytes < 1024) {
            return totalBytes + " B";
        }

        double kb = totalBytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.2f KB", kb);
        }

        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.2f MB", mb);
        }

        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }
}
