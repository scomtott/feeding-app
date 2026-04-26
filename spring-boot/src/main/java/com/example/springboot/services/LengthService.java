package com.example.springboot.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.springboot.models.LengthCentile;
import com.example.springboot.models.LengthEntry;
import com.example.springboot.persistence.LengthRepository;
import com.example.springboot.utilities.LengthCentileParametersData;
import com.example.springboot.utilities.NormalDistributionUtility;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LengthService {
    private final LengthRepository repository;

    public List<LengthEntry> getAllEntries(Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date"));
    }

    public List<LengthEntry> getEntriesByDate(LocalDate date, Sort.Direction direction) {
        return repository.findAll(Sort.by(direction, "date")).stream()
            .filter(entry -> entry.getDate().equals(date))
            .toList();
    }

    public LengthEntry saveEntry(LengthEntry entry) {
        return repository.save(entry);
    }

    public List<LengthEntry> saveAllEntries(List<LengthEntry> entries) {
        return repository.saveAll(entries);
    }

    public List<LengthCentile> calculateCentiles() {
        log.info("Calculating centiles for length entries");
        List<LengthEntry> entries = repository.findAll(Sort.by(Sort.Direction.ASC, "date"));
        List<LengthCentile> centiles = new ArrayList<>(entries.size());
        for (LengthEntry entry : entries) {
            double centileValue = NormalDistributionUtility.calculateCentileValue(
                entry.getLengthInCentimetres(),
                entry.getDate(),
                LengthCentileParametersData.INSTANCE,
                "length(cm)",
                1.0,
                log
            );
            centiles.add(new LengthCentile(entry.getDate(), entry.getLengthInCentimetres(), centileValue));
        }
        return centiles;
    }

    public void deleteEntry(Long id) {
        log.info("Deleting length entry with id: {}", id);
        repository.deleteById(id);
    }

    public void batchDelete(List<Long> ids) {
        log.info("Batch deleting {} length entries", ids.size());
        for (Long id : ids) {
            repository.deleteById(id);
        }
    }
}