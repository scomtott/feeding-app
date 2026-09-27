package com.example.springboot.models;

import java.time.Instant;

public record BackendFileLogEntry(
    Instant timestamp,
    String sourceClass,
    String logLevel,
    String category,
    String message
) {
}
