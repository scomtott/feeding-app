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

import com.example.springboot.models.LengthCentile;
import com.example.springboot.models.LengthEntry;
import com.example.springboot.services.LengthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/length")
@RequiredArgsConstructor
public class LengthController {

    private final LengthService lengthService;

    @GetMapping("/entries")
    public List<LengthEntry> getEntries(
        @RequestParam(required = false) LocalDate date,
        @RequestParam(required = false) Sort.Direction direction
    ) {
        if (direction == null) {
            direction = Sort.Direction.ASC;
        }

        if (date == null) {
            return lengthService.getAllEntries(direction);
        }
        return lengthService.getEntriesByDate(date, direction);
    }

    @GetMapping("/centiles")
    public List<LengthCentile> getCentiles() {
        return lengthService.calculateCentiles();
    }

    @PostMapping("/entries")
    public LengthEntry save(@RequestBody LengthEntry entry) {
        return lengthService.saveEntry(entry);
    }

    @PostMapping("/entries/batch")
    public List<LengthEntry> batchSave(@RequestBody List<LengthEntry> entries) {
        return lengthService.saveAllEntries(entries);
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable Long id) {
        lengthService.deleteEntry(id);
    }

    @DeleteMapping("/entries/batch")
    public void batchDelete(@RequestBody List<Long> ids) {
        lengthService.batchDelete(ids);
    }
}