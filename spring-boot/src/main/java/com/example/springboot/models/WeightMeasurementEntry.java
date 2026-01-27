package com.example.springboot.models;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;


@Entity(name = "WeightMeasurementEntry")
@Table(indexes = {@Index(name = "idx_date", columnList = "date")})
public class WeightMeasurementEntry {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate date;

    private int weightInGrams;

    private MeasurementLocation location;

    public WeightMeasurementEntry() {
    }

    public WeightMeasurementEntry(LocalDate date, int weightInGrams) {
        this.date = date;
        this.weightInGrams = weightInGrams;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getWeightInGrams() {
        return weightInGrams;
    }

    public void setWeightInGrams(int weightInGrams) {
        this.weightInGrams = weightInGrams;
    }

    public MeasurementLocation getLocation() {
        return location;
    }

    public void setLocation(MeasurementLocation location) {
        this.location = location;
    }
}