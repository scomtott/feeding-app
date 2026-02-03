package com.example.springboot.models;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;


@Entity(name = "WeightEntry")
@Table(indexes = {@Index(name = "idx_date", columnList = "date")})
public class WeightEntry {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate date;

    private int weightInGrams;

    @Enumerated(EnumType.STRING)
    private MeasurementLocation location;

    public WeightEntry() {
    }

    public WeightEntry(LocalDate date, int weightInGrams, MeasurementLocation location) {
        this.date = date;
        this.weightInGrams = weightInGrams;
        this.location = location;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getWeightInGrams() {
        return weightInGrams;
    }

    public MeasurementLocation getLocation() {
        return location;
    }
}