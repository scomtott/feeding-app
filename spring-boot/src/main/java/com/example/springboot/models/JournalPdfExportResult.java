package com.example.springboot.models;

public record JournalPdfExportResult(
    String fileName,
    byte[] content
) {
}
