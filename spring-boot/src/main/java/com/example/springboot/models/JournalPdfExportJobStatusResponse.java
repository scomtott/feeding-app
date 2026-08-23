package com.example.springboot.models;

import java.time.Instant;
import java.time.LocalDate;

public record JournalPdfExportJobStatusResponse(
    String jobId,
    String state,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    LocalDate from,
    LocalDate to,
    String fileName,
    boolean downloadable,
    String message
) {
}
