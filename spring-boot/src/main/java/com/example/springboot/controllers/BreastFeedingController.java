package com.example.springboot.controllers;

import com.example.springboot.models.BreastFeedingEntry;
import com.example.springboot.models.DailyFeedingTotal;
import com.example.springboot.services.BreastFeedingService;

import java.time.LocalDate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/breastfeeding")
@RequiredArgsConstructor
@Slf4j
public class BreastFeedingController {

    private final BreastFeedingService breastFeedingService;

    @PostMapping("/entries")
    public BreastFeedingEntry save(@RequestBody BreastFeedingEntry entry) {
        return breastFeedingService.saveEntry(entry);
    }

    @PostMapping("/entries/batch")
    public List<BreastFeedingEntry> batchSave(@RequestBody List<BreastFeedingEntry> entries) {
        return breastFeedingService.saveAllEntries(entries);
    }

    @GetMapping("/entries")
    public List<BreastFeedingEntry> getEntries(@RequestParam(required = false) LocalDate date, @RequestParam(required = false) Sort.Direction direction) {
        if (direction == null) {
            direction = Sort.Direction.ASC;
        }
        
        if (date == null) {
            return breastFeedingService.getAllEntries(direction);
        }
        return breastFeedingService.getEntriesByDate(date, direction);
    }

    @GetMapping("/dailyTotals")
    public List<DailyFeedingTotal> dailyTotals() {
        return breastFeedingService.calculateDailyTotals();
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable Long id) {
        breastFeedingService.deleteEntry(id);
    }

    @DeleteMapping("/entries/batch")
    public void batchDelete(@RequestBody List<Long> ids) {
        breastFeedingService.batchDelete(ids);
    }
}