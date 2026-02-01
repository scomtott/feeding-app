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

private final LocalDate MIN_DATE = LocalDate.of(2020, 1, 1);
private final LocalDate MAX_DATE = LocalDate.of(2100, 1, 1);

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
        return calculateDailyTotals(null, null);
    }

    public List<DailyFeedingTotal> calculateDailyTotals(LocalDate startDate, LocalDate endDate) {
        log.info("Calculate daily feeding totals range from {} to {}", startDate, endDate);
        if (startDate == null) {
            startDate = MIN_DATE;
        }
        if (endDate == null) {
            endDate = MAX_DATE;
        }
        log.info("Using date range from {} to {}", startDate, endDate);

        List<BreastFeedingEntry> orderedEntries = repository.findByDateBetween(startDate, endDate);
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