package com.example.springboot.services;

import java.util.List;

import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.ArrayList;

import com.example.springboot.models.PumpingEntry;
import com.example.springboot.persistence.PumpingEntryRepository;
import com.example.springboot.models.DailyPumpingTotal;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PumpingService {
    private final PumpingEntryRepository repository;

    private final LocalDate MIN_DATE = LocalDate.of(2020, 1, 1);
private final LocalDate MAX_DATE = LocalDate.of(2100, 1, 1);

    public List<PumpingEntry> getAllEntries(Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date", "time"));
    }

    public List<PumpingEntry> getEntriesByDate(java.time.LocalDate date, Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date", "time")).stream()
                .filter(entry -> entry.getDate().equals(date))
                .toList();
    }

    public PumpingEntry saveEntry(PumpingEntry entry) {
        return repository.save(entry);
    }

    public List<PumpingEntry> saveAllEntries(List<PumpingEntry> entries) {
        return repository.saveAll(entries);
    }

    public List<DailyPumpingTotal> calculateDailyTotals() {
        return calculateDailyTotals(null, null);
    }

    public List<DailyPumpingTotal> calculateDailyTotals(LocalDate startDate, LocalDate endDate) {
        log.info("Calculating daily pumping totals from {} to {}", startDate, endDate);
        if (startDate == null) {
            startDate = MIN_DATE;
        }
        if (endDate == null) {
            endDate = MAX_DATE;
        }

        log.info("Using date range from {} to {}", startDate, endDate);
        
        List<PumpingEntry> orderedEntries = repository.findByDateBetween(startDate, endDate);
        List<DailyPumpingTotal> totals = new ArrayList<>();

        if (orderedEntries.isEmpty()) {
            return totals;
        }

        LocalDate currentDate = orderedEntries.get(0).getDate();
        List<PumpingEntry> currentDayEntries = new ArrayList<>();
        for (PumpingEntry entry : orderedEntries) {
            if (!entry.getDate().equals(currentDate)) {
                totals.add(DailyPumpingTotal.fromPumpingEntryList(currentDayEntries));
                currentDayEntries.clear();
                currentDayEntries.add(entry);
                currentDate = entry.getDate();
            }
            else
            {
                currentDayEntries.add(entry);
            }
        }

        totals.add(DailyPumpingTotal.fromPumpingEntryList(currentDayEntries));

        return totals;
    }

    public void deleteEntry(Long id) {
        repository.deleteById(id);
    }

    public void batchDelete(List<Long> ids) {
        ids.forEach(repository::deleteById);
    }
}