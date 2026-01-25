package com.example.springboot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity(name = "BreastFeedingEntry")
@Table(indexes = {@Index(name = "idx_date_time", columnList = "date,time")})
public class BreastFeedingEntry {

    @Id
    @GeneratedValue
    private Long id;

    private LocalDate date;

    private LocalTime time;

    private int bottleAmount;

    private int leftBreastAmount;

    private int rightBreastAmount;

    public BreastFeedingEntry() {
    }

    public BreastFeedingEntry(LocalDateTime startTime, int bottleAmount, int leftBreastAmount, int rightBreastAmount) {
        this.date = startTime.toLocalDate();
        this.time = startTime.toLocalTime();
        this.bottleAmount = bottleAmount;
        this.leftBreastAmount = leftBreastAmount;
        this.rightBreastAmount = rightBreastAmount;
    }

    public long getId() {
        return id;
    }

    public int getTotalBreastAmount() {
        return leftBreastAmount + rightBreastAmount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public int getBottleAmount() {
        return bottleAmount;
    }

    public void setBottleAmount(int bottleAmount) {
        this.bottleAmount = bottleAmount;
    }

    public int getLeftBreastAmount() {
        return leftBreastAmount;
    }

    public void setLeftBreastAmount(int leftBreastAmount) {
        this.leftBreastAmount = leftBreastAmount;
    }

    public int getRightBreastAmount() {
        return rightBreastAmount;
    }

    public void setRightBreastAmount(int rightBreastAmount) {
        this.rightBreastAmount = rightBreastAmount;
    }

    // getters/setters
}
