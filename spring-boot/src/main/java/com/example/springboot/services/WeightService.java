package com.example.springboot.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.springboot.models.Centile;
import com.example.springboot.models.MeasurementLocation;
import com.example.springboot.models.WeightEntry;
import com.example.springboot.persistence.WeightRepository;
import com.example.springboot.utilities.LinearRegressionResult;
import com.example.springboot.utilities.LinearRegressionUtility;
import com.example.springboot.utilities.NormalDistributionUtility;

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

    public void deleteEntry(Long id) {
        log.info("Deleting weight entry with id: {}", id);
        repository.deleteById(id);
    }

    public List<Centile> predictWeightTrend(int daysIntoFuture) {
        List<WeightEntry> entries = repository.findAll(Sort.by(Sort.Direction.ASC, "date"));
        long[] xValues = entries.stream().mapToLong(e -> e.getDate().toEpochDay()).toArray();
        int[] yValues = entries.stream().mapToInt(WeightEntry::getWeightInGrams).toArray();

        LinearRegressionResult regressionResult = LinearRegressionUtility.performLinearRegression(xValues, yValues, log);

        LocalDate lastDate = entries.get(entries.size() - 1).getDate();

        List<WeightEntry> predictedEntries = new ArrayList<>();

        for (int i = 0; i < daysIntoFuture; i++) {
            LocalDate newDate = lastDate.plusDays(i + 1);
            int predictedWeight = Math.toIntExact(Math.round(regressionResult.slope() * newDate.toEpochDay() + regressionResult.intercept()));
            WeightEntry newEntry = new WeightEntry(newDate, predictedWeight, MeasurementLocation.HOME);
            predictedEntries.add(newEntry);
        }

        List<Centile> centiles = new ArrayList<>(predictedEntries.size());
        StringBuilder logBuilder1 = new StringBuilder("Date:\n");
        StringBuilder logBuilder2 = new StringBuilder("Weight:\n");
        StringBuilder logBuilder3 = new StringBuilder("Centile:\n");
        for (WeightEntry entry : predictedEntries) {
            Centile centile = NormalDistributionUtility.calculateCentile(entry, log);
            logBuilder1.append(String.format("%s\n", entry.getDate()));
            logBuilder2.append(String.format("%d\n", entry.getWeightInGrams()));
            logBuilder3.append(String.format("%.2f\n", centile.centileValue()));
            centiles.add(centile);
        }

        log.info(logBuilder1.toString());
        log.info(logBuilder2.toString());
        log.info(logBuilder3.toString());
        return centiles;
    }
}