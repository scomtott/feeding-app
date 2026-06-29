package com.example.springboot.models;

public record OneDriveBackupCredentials(
    String tenantId,
    String clientId,
    String refreshToken,
    String remoteRootFolder
) {
}
