package com.example.springboot.models;

public record BackendFileLogStorageResponse(
    long totalBytes,
    String totalSizeDisplay,
    int fileCount
) {
}
