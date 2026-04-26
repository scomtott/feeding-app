package com.example.springboot.models;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity(name = "LengthEntry")
@Table(indexes = {@Index(name = "idx_length_date", columnList = "date")})
public class LengthEntry {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate date;

    @Column(name = "length_in_centimetres")
    private double lengthInCentimetres;

    @Enumerated(EnumType.STRING)
    private MeasurementLocation location;

    public LengthEntry() {
    }

    public LengthEntry(LocalDate date, double lengthInCentimetres, MeasurementLocation location) {
        this.date = date;
        this.lengthInCentimetres = lengthInCentimetres;
        this.location = location;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public double getLengthInCentimetres() {
        return lengthInCentimetres;
    }

    public MeasurementLocation getLocation() {
        return location;
    }
}