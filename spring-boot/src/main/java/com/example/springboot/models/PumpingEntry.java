package com.example.springboot.models;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;


@Entity(name = "PumpingEntry")
@Table(indexes = {@Index(name = "idx_date_time", columnList = "date,time")})
public class PumpingEntry {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate date;

    private LocalTime time;

    private int amount;

    private boolean isPowerPump;

    public PumpingEntry() {
    }

    public PumpingEntry(LocalDate date, LocalTime time, int amount, boolean isPowerPump) {
        this.date = date;
        this.time = time;
        this.amount = amount;
        this.isPowerPump = isPowerPump;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isPowerPump() {
        return isPowerPump;
    }
}