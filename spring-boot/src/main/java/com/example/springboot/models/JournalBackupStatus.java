package com.example.springboot.models;

import java.time.Instant;

public record JournalBackupStatus(
    String state,
    Instant lastRunAt,
    Instant lastSuccessAt,
    boolean running,
    int filesScanned,
    int filesUploaded,
    String message
) {
}
