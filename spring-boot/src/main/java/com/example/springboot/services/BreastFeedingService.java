package com.example.springboot.services;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;

import com.example.springboot.models.BreastFeedingEntry;
import com.example.springboot.persistence.BreastFeedingEntryRepository;
import com.example.springboot.models.DailyFeedingTotal;


@Service
@RequiredArgsConstructor
@Slf4j
public class BreastFeedingService {

private final BreastFeedingEntryRepository repository;

    public List<BreastFeedingEntry> getAllEntries(Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date", "time"));
    }

    public List<BreastFeedingEntry> getEntriesByDate(LocalDate date, Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date", "time")).stream()
                .filter(entry -> entry.getDate().equals(date))
                .toList();
    }

    public BreastFeedingEntry saveEntry(BreastFeedingEntry entry) {
        return repository.save(entry);
    }

    public List<BreastFeedingEntry> saveAllEntries(List<BreastFeedingEntry> entries) {
        return repository.saveAll(entries);
    }

    public List<DailyFeedingTotal> calculateDailyTotals() {
        List<BreastFeedingEntry> orderedEntries = repository.findAll(Sort.by(Sort.Direction.ASC, "date", "time"));
        List<DailyFeedingTotal> totals = new ArrayList<>();

        if (orderedEntries.isEmpty()) {
            return totals;
        }

        LocalDate currentDate = orderedEntries.get(0).getDate();
        List<BreastFeedingEntry> currentDayEntries = new ArrayList<>();
        for (BreastFeedingEntry entry : orderedEntries) {
            if (!entry.getDate().equals(currentDate)) {
                totals.add(DailyFeedingTotal.fromBreastFeedingEntryList(currentDayEntries));
                currentDayEntries.clear();
                currentDayEntries.add(entry);
                currentDate = entry.getDate();
            }
            else
            {
                currentDayEntries.add(entry);
            }
        }

        totals.add(DailyFeedingTotal.fromBreastFeedingEntryList(currentDayEntries));

        return totals;
    }

    public void deleteEntry(Long id) {
        repository.deleteById(id);
    }

    public void batchDelete(List<Long> ids) {
        ids.forEach(repository::deleteById);
    }
}