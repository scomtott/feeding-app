package com.example.springboot.models;

public record JournalBackupBootstrapStartRequest(
    String clientId,
    String tenantId,
    String remoteRootFolder
) {
}
