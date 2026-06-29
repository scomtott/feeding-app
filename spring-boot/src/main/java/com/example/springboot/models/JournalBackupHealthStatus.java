package com.example.springboot.models;

import java.time.Instant;

public record JournalBackupHealthStatus(
    boolean enabled,
    Instant checkedAt,
    boolean credentialsPresent,
    boolean tokenAcquired,
    boolean driveReachable,
    String driveName,
    String message
) {
}
