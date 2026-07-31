package com.example.springboot.models;

import java.time.LocalDate;

public record InvestmentUnitsRequest(
    LocalDate date,
    String fundCode,
    double unitsHeld
) {
}
