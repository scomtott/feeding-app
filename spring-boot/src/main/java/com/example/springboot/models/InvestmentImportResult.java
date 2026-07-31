package com.example.springboot.models;

import java.time.LocalDate;
import java.util.List;

public record InvestmentImportResult(
    LocalDate startDate,
    LocalDate endDate,
    int fundsProcessed,
    int rowsInserted,
    int rowsUpdated,
    List<String> messages
) {
}
