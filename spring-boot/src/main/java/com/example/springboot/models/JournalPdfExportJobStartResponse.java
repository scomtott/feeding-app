package com.example.springboot.models;

import java.time.Instant;

public record JournalPdfExportJobStartResponse(
    String jobId,
    String state,
    Instant createdAt,
    String message
) {
}
