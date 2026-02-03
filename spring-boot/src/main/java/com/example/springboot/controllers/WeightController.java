package com.example.springboot.controllers;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;

import com.example.springboot.services.WeightService;
import com.example.springboot.models.WeightEntry;
import com.example.springboot.models.Centile;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/weight")
@RequiredArgsConstructor
@Slf4j
public class WeightController {

    private final WeightService weightService;

    @GetMapping("/entries")
    public List<WeightEntry> getEntries(@RequestParam(required = false) LocalDate date, @RequestParam(required = false) Sort.Direction direction) {
        if (direction == null) {
            direction = Sort.Direction.ASC;
        }
        
        if (date == null) {
            return weightService.getAllEntries(direction);
        }
        return weightService.getEntriesByDate(date, direction);
    }

    @GetMapping("/centiles")
    public List<Centile> getCentiles() {
        return weightService.calculateCentiles();
    }

    @PostMapping("/entries")
    public WeightEntry save(@RequestBody WeightEntry entry) {
        return weightService.saveEntry(entry);
    }

    @PostMapping("/entries/batch")
    public List<WeightEntry> batchSave(@RequestBody List<WeightEntry> entries) {
        return weightService.saveAllEntries(entries);
    }
}
