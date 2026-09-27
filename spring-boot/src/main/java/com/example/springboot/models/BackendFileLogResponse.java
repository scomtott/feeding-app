package com.example.springboot.models;

import java.util.List;

public record BackendFileLogResponse(
    List<BackendFileLogEntry> logs,
    int returnedCount,
    long totalBytes,
    String totalSizeDisplay,
    int fileCount
) {
}
