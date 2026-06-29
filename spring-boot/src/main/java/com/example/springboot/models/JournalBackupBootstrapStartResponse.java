package com.example.springboot.models;

import java.time.Instant;

public record JournalBackupBootstrapStartResponse(
    String requestId,
    String userCode,
    String verificationUri,
    String verificationUriComplete,
    String message,
    Instant expiresAt,
    int pollIntervalSeconds
) {
}
