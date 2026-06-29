package com.example.springboot.models;

import java.time.Instant;
import java.time.LocalDate;

public record JournalDayEntry(
    LocalDate date,
    boolean exists,
    String markdown,
    Instant lastModified
) {
}
