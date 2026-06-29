package com.example.springboot.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.springboot.models.JournalDayEntry;
import com.example.springboot.models.JournalImageUploadResponse;
import com.example.springboot.models.JournalMonthIndexResponse;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JournalService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/bmp"
    );

    private final Path markdownRoot;
    private final Path imageRoot;

    public JournalService(
        @Value("${journal.storage.markdown-root:./data/journal/entries}") String markdownRoot,
        @Value("${journal.storage.image-root:./data/journal/images}") String imageRoot
    ) {
        this.markdownRoot = Paths.get(markdownRoot).toAbsolutePath().normalize();
        this.imageRoot = Paths.get(imageRoot).toAbsolutePath().normalize();
        ensureDirectoriesExist();
    }

    public JournalDayEntry getDayEntry(LocalDate date) {
        Path filePath = resolveMarkdownPath(date);
        if (!Files.exists(filePath)) {
            return new JournalDayEntry(date, false, "", null);
        }

        try {
            String markdown = Files.readString(filePath, StandardCharsets.UTF_8);
            return new JournalDayEntry(date, true, markdown, Files.getLastModifiedTime(filePath).toInstant());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read journal entry", ex);
        }
    }

    public JournalDayEntry saveDayEntry(LocalDate date, String markdown) {
        Path filePath = resolveMarkdownPath(date);
        String content = markdown == null ? "" : markdown;

        try {
            Files.writeString(
                filePath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            log.info("Saved journal entry for {}", date);
            return getDayEntry(date);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save journal entry", ex);
        }
    }

    public JournalMonthIndexResponse getMonthIndex(int year, int month) {
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.of(year, month);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid year or month");
        }

        if (!Files.exists(markdownRoot)) {
            return new JournalMonthIndexResponse(yearMonth.getYear(), yearMonth.getMonthValue(), List.of());
        }

        List<LocalDate> dates = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(markdownRoot, "*.md")) {
            for (Path file : stream) {
                LocalDate parsedDate = parseDateFromFileName(file.getFileName().toString());
                if (parsedDate != null && YearMonth.from(parsedDate).equals(yearMonth)) {
                    dates.add(parsedDate);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list journal entries for month", ex);
        }

        dates.sort(Comparator.naturalOrder());
        return new JournalMonthIndexResponse(yearMonth.getYear(), yearMonth.getMonthValue(), dates);
    }

    public JournalImageUploadResponse uploadImage(LocalDate date, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported image type");
        }

        String extension = extractSafeExtension(image.getOriginalFilename(), contentType);
        String fileName = UUID.randomUUID() + extension;
        String dayPath = String.format("%04d/%02d/%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        Path targetDirectory = resolveImageDirectory(dayPath);
        Path targetFile = targetDirectory.resolve(fileName).normalize();

        if (!targetFile.startsWith(imageRoot)) {
            throw new IllegalArgumentException("Invalid image target path");
        }

        try {
            Files.createDirectories(targetDirectory);
            image.transferTo(targetFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store image", ex);
        }

        String url = "/journal-media/" + dayPath + "/" + fileName;
        String markdown = "![]("
            + url
            + ")";
        log.info("Stored journal image for {} at {}", date, targetFile);
        return new JournalImageUploadResponse(url, markdown, fileName);
    }

    private LocalDate parseDateFromFileName(String fileName) {
        if (!fileName.endsWith(".md")) {
            return null;
        }

        String datePart = fileName.substring(0, fileName.length() - 3);
        try {
            return LocalDate.parse(datePart, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Path resolveMarkdownPath(LocalDate date) {
        Path filePath = markdownRoot.resolve(date.format(DATE_FORMATTER) + ".md").normalize();
        if (!filePath.startsWith(markdownRoot)) {
            throw new IllegalArgumentException("Invalid journal path");
        }
        return filePath;
    }

    private Path resolveImageDirectory(String relativePath) {
        Path directory = imageRoot.resolve(relativePath).normalize();
        if (!directory.startsWith(imageRoot)) {
            throw new IllegalArgumentException("Invalid image directory path");
        }
        return directory;
    }

    private String extractSafeExtension(String originalFileName, String contentType) {
        if (originalFileName != null) {
            int extensionIndex = originalFileName.lastIndexOf('.');
            if (extensionIndex >= 0 && extensionIndex < originalFileName.length() - 1) {
                String candidate = originalFileName.substring(extensionIndex).toLowerCase(Locale.ROOT);
                if (candidate.matches("\\.[a-z0-9]{1,8}")) {
                    return candidate;
                }
            }
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            default -> ".jpg";
        };
    }

    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(markdownRoot);
            Files.createDirectories(imageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize journal storage directories", ex);
        }
    }
}
