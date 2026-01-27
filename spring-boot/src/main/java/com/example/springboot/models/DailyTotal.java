package com.example.springboot.models;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;

public record DailyTotal(LocalDate date, int breastTotal, int bottleTotal, int breastFeedingCount, int bottleCount) {
    
    public DailyTotal() {
        this(null, 0, 0, 0, 0);
    }

    public static DailyTotal fromBreastFeedingEntryList(List<BreastFeedingEntry> entries, Logger logger) {
        if (entries.isEmpty()) {
            return new DailyTotal();
        }
        
        LocalDate date = entries.get(0).getDate();
        int sum = 0;
        for (BreastFeedingEntry entry : entries) {
            sum += entry.getTotalBreastAmount();
            if (!entry.getDate().equals(date)) {
                throw new IllegalArgumentException("All entries must have the same date");
            }
        }

        logger.info("Calculating DailyTotal for date: " + date + " with " + entries.size() + " entries. Sum: " + sum);

        int breastTotal = entries.stream().mapToInt(BreastFeedingEntry::getTotalBreastAmount).sum();
        int bottleTotal = entries.stream().mapToInt(BreastFeedingEntry::getBottleAmount).sum();
        int breastFeedingCount = (int) entries.stream().filter(e -> e.getTotalBreastAmount() > 0).count();
        int bottleCount = (int) entries.stream().filter(e -> e.getBottleAmount() > 0).count();
        return new DailyTotal(date, breastTotal, bottleTotal, breastFeedingCount, bottleCount);
    }
}