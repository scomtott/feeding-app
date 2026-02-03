package com.example.springboot.services;

import java.util.List;

import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.ArrayList;

import com.example.springboot.models.WeightEntry;
import com.example.springboot.persistence.WeightRepository;
import com.example.springboot.models.Centile;
import com.example.springboot.utilities.NormalDistributionUtility;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeightService {
    private final WeightRepository repository;

    public List<WeightEntry> getAllEntries(Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date"));
    }

    public List<WeightEntry> getEntriesByDate(LocalDate date, Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date")).stream()
                .filter(entry -> entry.getDate().equals(date))
                .toList();
    }

    public WeightEntry saveEntry(WeightEntry entry) {
        return repository.save(entry);
    }

    public List<WeightEntry> saveAllEntries(List<WeightEntry> entries) {
        return repository.saveAll(entries);
    }

    public List<Centile> calculateCentiles() {
        log.info("Calculating centiles for weight entries");
        List<WeightEntry> entries = repository.findAll(Sort.by(Sort.Direction.ASC, "date"));
        List<Centile> centiles = new ArrayList<>(entries.size());
        for (WeightEntry entry : entries) {
            Centile centile = NormalDistributionUtility.calculateCentile(entry, log);
            centiles.add(centile);
        }
        return centiles;
    }
}