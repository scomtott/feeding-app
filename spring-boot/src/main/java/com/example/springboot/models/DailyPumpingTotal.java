package com.example.springboot.models;

import java.time.LocalDate;
import java.util.List;

public record DailyPumpingTotal(LocalDate date, int amount, int count) {
    
    public DailyPumpingTotal() {
        this(null, 0, 0);
    }

    public static DailyPumpingTotal fromPumpingEntryList(List<PumpingEntry> entries) {
        if (entries.isEmpty()) {
            return new DailyPumpingTotal();
        }
        
        LocalDate date = entries.get(0).getDate();
        for (PumpingEntry entry : entries) {
            if (!entry.getDate().equals(date)) {
                throw new IllegalArgumentException("All entries must have the same date");
            }
        }

        int totalAmount = entries.stream().mapToInt(PumpingEntry::getAmount).sum();
        int count = entries.size();
        return new DailyPumpingTotal(date, totalAmount, count);
    }
}