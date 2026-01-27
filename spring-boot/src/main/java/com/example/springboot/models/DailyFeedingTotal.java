package com.example.springboot.models;

import java.time.LocalDate;
import java.util.List;

public record DailyFeedingTotal(LocalDate date, int breastTotal, int bottleTotal, int breastFeedingCount, int bottleCount) {
    
    public DailyFeedingTotal() {
        this(null, 0, 0, 0, 0);
    }

    public static DailyFeedingTotal fromBreastFeedingEntryList(List<BreastFeedingEntry> entries) {
        if (entries.isEmpty()) {
            return new DailyFeedingTotal();
        }
        
        LocalDate date = entries.get(0).getDate();
        int sum = 0;
        for (BreastFeedingEntry entry : entries) {
            sum += entry.getTotalBreastAmount();
            if (!entry.getDate().equals(date)) {
                throw new IllegalArgumentException("All entries must have the same date");
            }
        }

        int breastTotal = entries.stream().mapToInt(BreastFeedingEntry::getTotalBreastAmount).sum();
        int bottleTotal = entries.stream().mapToInt(BreastFeedingEntry::getBottleAmount).sum();
        int breastFeedingCount = (int) entries.stream().filter(e -> e.getTotalBreastAmount() > 0).count();
        int bottleCount = (int) entries.stream().filter(e -> e.getBottleAmount() > 0).count();
        return new DailyFeedingTotal(date, breastTotal, bottleTotal, breastFeedingCount, bottleCount);
    }
}