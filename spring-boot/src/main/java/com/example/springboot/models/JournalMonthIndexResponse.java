package com.example.springboot.models;

import java.time.LocalDate;
import java.util.List;

public record JournalMonthIndexResponse(
    int year,
    int month,
    List<LocalDate> datesWithEntries
) {
}
