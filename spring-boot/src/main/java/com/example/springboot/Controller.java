package com.example.springboot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/breastfeeding")
public class Controller {

    private final Repository repo;

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public Controller(Repository repo) {
        this.repo = repo;
    }

    @PostMapping("/entries")
    public BreastFeedingEntry save(@RequestBody BreastFeedingEntry entry) {
        return repo.save(entry);
    }

    @PostMapping("/entries/batch")
    public List<BreastFeedingEntry> batchSave(@RequestBody List<BreastFeedingEntry> entries) {
        return repo.saveAll(entries);
    }

    @GetMapping("/entries")
    public List<BreastFeedingEntry> getEntries(@RequestParam(required = false) LocalDate date) {
        List<BreastFeedingEntry> all = repo.findAll(Sort.by(Sort.Direction.ASC, "date", "time"));
        if (date == null) {
            return all;
        }
        return all.stream()
            .filter(e -> e.getDate().equals(date))
            .toList();
    }

    @GetMapping("/dailyTotals")
    public List<DailyTotal> dailyTotals() {
        List<BreastFeedingEntry> orderedEntries = repo.findAll(Sort.by(Sort.Direction.ASC, "date", "time"));
        List<DailyTotal> totals = new ArrayList<>();

        if (orderedEntries.isEmpty()) {
            return totals;
        }

        LocalDate currentDate = orderedEntries.get(0).getDate();
        List<BreastFeedingEntry> currentDayEntries = new ArrayList<>();
        for (BreastFeedingEntry entry : orderedEntries) {
            if (!entry.getDate().equals(currentDate)) {
                totals.add(DailyTotal.fromBreastFeedingEntryList(currentDayEntries, this.logger));
                currentDayEntries.clear();
                currentDayEntries.add(entry);
                currentDate = entry.getDate();
            }
            else
            {
                currentDayEntries.add(entry);
            }
        }

        totals.add(DailyTotal.fromBreastFeedingEntryList(currentDayEntries, this.logger));

        return totals; 
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable Long id) {
        repo.deleteById(id);
    }

    @DeleteMapping("/entries/batch")
    public void batchDelete(@RequestBody List<Long> ids) {
        ids.forEach(repo::deleteById);
    }
}