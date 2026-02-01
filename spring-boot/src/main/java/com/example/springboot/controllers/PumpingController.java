package com.example.springboot.controllers;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;

import com.example.springboot.models.PumpingEntry;
import com.example.springboot.models.DailyPumpingTotal;
import com.example.springboot.services.PumpingService;



import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pumping")
@RequiredArgsConstructor
@Slf4j
public class PumpingController {
    private final PumpingService pumpingService;

    @PostMapping("/entries")
    public PumpingEntry save(@RequestBody PumpingEntry entry) {
        return pumpingService.saveEntry(entry);
    }

    @PostMapping("/entries/batch")
    public List<PumpingEntry> batchSave(@RequestBody List<PumpingEntry> entries) {
        return pumpingService.saveAllEntries(entries);
    }

    @GetMapping("/entries")
    public List<PumpingEntry> getEntries(@RequestParam(required = false) LocalDate date, @RequestParam(required = false) Sort.Direction direction) {
        if (direction == null) {
            direction = Sort.Direction.ASC;
        }
        
        if (date == null) {
            return pumpingService.getAllEntries(direction);
        }
        return pumpingService.getEntriesByDate(date, direction);
    }

    @GetMapping("/dailyTotals")
    public List<DailyPumpingTotal> dailyTotals(@RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate) {
        log.info("Received request for daily pumping totals from {} to {}", startDate, endDate);
        return pumpingService.calculateDailyTotals(startDate, endDate);
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable Long id) {
        pumpingService.deleteEntry(id);
    }

    @DeleteMapping("/entries/batch")
    public void batchDelete(@RequestBody List<Long> ids) {
        pumpingService.batchDelete(ids);
    }
}