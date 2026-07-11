package com.example.springboot.utilities;

import java.time.LocalDate;

public record PredictedMeasurement(LocalDate date, double value) {
}