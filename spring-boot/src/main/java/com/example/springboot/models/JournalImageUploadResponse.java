package com.example.springboot.models;

public record JournalImageUploadResponse(
    String url,
    String markdown,
    String fileName
) {
}
