package com.example.springboot.controllers;

import com.example.springboot.models.LoggingEntry;
import com.example.springboot.persistence.LoggingRepository;

import java.time.LocalDate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class LoggingController {

    private final LoggingRepository loggingRepository;
    
    @GetMapping()
    public List<LoggingEntry> getLogs(@RequestParam(required = false) LocalDate date)
    {
        if (date != null) {
            return loggingRepository.findAll(Sort.by(Sort.Direction.ASC, "date", "time")).stream()
                    .filter(entry -> entry.getDate().isAfter(date))
                    .toList();
        } else {
            return loggingRepository.findAll(Sort.by(Sort.Direction.ASC, "date", "time"));
        }
    }
    
    @PostMapping()
    public List<LoggingEntry> logMessage(@RequestBody LoggingEntry entry) {
        loggingRepository.save(entry);
        return List.of(entry);
    }

    @PostMapping("/batch")
    public List<LoggingEntry> logMessages(@RequestBody List<LoggingEntry> entries) {
        return loggingRepository.saveAll(entries);
    }

    @DeleteMapping()
    public void deleteLogsByDate(@RequestParam LocalDate date) {
        loggingRepository.findAll().stream()
            .filter(entry -> entry.getDate().isBefore(date))
            .forEach(entry -> loggingRepository.deleteById(entry.getId()));
    }
}