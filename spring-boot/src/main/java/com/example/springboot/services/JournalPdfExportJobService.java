package com.example.springboot.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.springboot.models.JournalPdfExportJobStartResponse;
import com.example.springboot.models.JournalPdfExportJobStatusResponse;
import com.example.springboot.models.JournalPdfExportResult;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JournalPdfExportJobService {

    private static final Duration JOB_RETENTION = Duration.ofHours(24);
    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_RUNNING = "RUNNING";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_FAILED = "FAILED";

    private final JournalPdfExportService journalPdfExportService;
    private final Path exportRoot;
    private final Map<String, ExportJob> jobsById = new ConcurrentHashMap<>();

    public JournalPdfExportJobService(
        JournalPdfExportService journalPdfExportService,
        @Value("${journal.export.storage.root:./data/journal/exports}") String exportRoot
    ) {
        this.journalPdfExportService = journalPdfExportService;
        this.exportRoot = Paths.get(exportRoot).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.exportRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize journal export directory", ex);
        }
    }

    public JournalPdfExportJobStartResponse startExportJob(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must be before or equal to 'to' date");
        }

        cleanupExpiredJobs();

        String jobId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        ExportJob job = new ExportJob(jobId, from, to, createdAt);
        jobsById.put(jobId, job);

        CompletableFuture.runAsync(() -> runExportJob(jobId));
        return new JournalPdfExportJobStartResponse(
            jobId,
            STATE_PENDING,
            createdAt,
            "Export job queued"
        );
    }

    public Optional<JournalPdfExportJobStatusResponse> getJobStatus(String jobId) {
        cleanupExpiredJobs();
        ExportJob job = jobsById.get(jobId);
        return Optional.ofNullable(job).map(this::toStatusResponse);
    }

    public Optional<JournalPdfExportResult> loadCompletedExport(String jobId) {
        cleanupExpiredJobs();
        ExportJob job = jobsById.get(jobId);
        if (job == null || !STATE_COMPLETED.equals(job.state)) {
            return Optional.empty();
        }
        if (job.outputPath == null || !Files.exists(job.outputPath)) {
            return Optional.empty();
        }

        try {
            byte[] content = Files.readAllBytes(job.outputPath);
            return Optional.of(new JournalPdfExportResult(job.fileName, content));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read generated export file", ex);
        }
    }

    private void runExportJob(String jobId) {
        ExportJob job = jobsById.get(jobId);
        if (job == null) {
            return;
        }

        job.state = STATE_RUNNING;
        job.startedAt = Instant.now();
        job.message = "Export in progress";

        try {
            JournalPdfExportResult exportResult = journalPdfExportService.exportAsPdf(job.from, job.to);
            String normalizedFileName = sanitizeFileName(exportResult.fileName());
            Path outputPath = exportRoot.resolve(job.id + "-" + normalizedFileName).normalize();

            if (!outputPath.startsWith(exportRoot)) {
                throw new IllegalStateException("Invalid export output path");
            }

            Files.write(outputPath, exportResult.content());

            job.fileName = normalizedFileName;
            job.outputPath = outputPath;
            job.state = STATE_COMPLETED;
            job.completedAt = Instant.now();
            job.message = "Export completed";
        } catch (Exception ex) {
            job.state = STATE_FAILED;
            job.completedAt = Instant.now();
            job.message = ex.getMessage() == null ? "Export failed" : ex.getMessage();
            log.warn("Journal export job {} failed: {}", job.id, job.message, ex);
        }
    }

    private JournalPdfExportJobStatusResponse toStatusResponse(ExportJob job) {
        boolean downloadable = STATE_COMPLETED.equals(job.state)
            && job.outputPath != null
            && Files.exists(job.outputPath);
        return new JournalPdfExportJobStatusResponse(
            job.id,
            job.state,
            job.createdAt,
            job.startedAt,
            job.completedAt,
            job.from,
            job.to,
            job.fileName,
            downloadable,
            job.message
        );
    }

    private void cleanupExpiredJobs() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, ExportJob>> iterator = jobsById.entrySet().iterator();
        while (iterator.hasNext()) {
            ExportJob job = iterator.next().getValue();
            if (!job.isTerminal()) {
                continue;
            }

            Instant terminalAt = job.completedAt != null ? job.completedAt : job.createdAt;
            if (terminalAt == null || Duration.between(terminalAt, now).compareTo(JOB_RETENTION) < 0) {
                continue;
            }

            if (job.outputPath != null) {
                try {
                    Files.deleteIfExists(job.outputPath);
                } catch (IOException ex) {
                    log.warn("Failed deleting expired export file: {}", job.outputPath, ex);
                }
            }
            iterator.remove();
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "journal-export.pdf";
        }

        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.endsWith(".pdf") ? sanitized : sanitized + ".pdf";
    }

    private static final class ExportJob {
        private final String id;
        private final LocalDate from;
        private final LocalDate to;
        private final Instant createdAt;
        private volatile String state;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile String fileName;
        private volatile Path outputPath;
        private volatile String message;

        private ExportJob(String id, LocalDate from, LocalDate to, Instant createdAt) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.createdAt = createdAt;
            this.state = STATE_PENDING;
            this.message = "Export job queued";
        }

        private boolean isTerminal() {
            return STATE_COMPLETED.equals(state) || STATE_FAILED.equals(state);
        }
    }
}
