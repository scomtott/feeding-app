package com.example.springboot.models;

public record JournalBackupBootstrapPollResponse(
    String status,
    boolean configured,
    String message
) {
}
