package com.example.springboot.models;
import java.time.LocalDate;

public record Centile(LocalDate date, int weightInGrams, double centileValue) {
}