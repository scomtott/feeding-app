package com.example.springboot.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.springboot.models.Centile;
import com.example.springboot.models.WeightEntry;
import com.example.springboot.services.WeightService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    @GetMapping("/centiles/predict")
    public List<Centile> getCentilesWithPrediction(@RequestParam int daysToPredict) {
        return weightService.predictWeightTrend(daysToPredict);
    }

    @PostMapping("/entries")
    public WeightEntry save(@RequestBody WeightEntry entry) {
        return weightService.saveEntry(entry);
    }

    @PostMapping("/entries/batch")
    public List<WeightEntry> batchSave(@RequestBody List<WeightEntry> entries) {
        return weightService.saveAllEntries(entries);
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable Long id) {
        weightService.deleteEntry(id);
    }
}
