package com.example.springboot.models;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity(name = "LoggingEntry")
@Table(indexes = {@Index(name = "idx_date_time", columnList = "date,time")})
public class LoggingEntry {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate date;

    private LocalTime time;

    private String message;

    private LogLevel logLevel;

    public LoggingEntry() {
    }

    public LoggingEntry(LocalDateTime logTime, String message, LogLevel logLevel) {
        this.date = logTime.toLocalDate();
        this.time = logTime.toLocalTime();
        this.message = message;
    }

    public LoggingEntry(LocalDate date, LocalTime time, String message, LogLevel logLevel) {
        this.date = date;
        this.time = time;
        this.message = message;
    }

    public long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public String getMessage() {
        return message;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }
}