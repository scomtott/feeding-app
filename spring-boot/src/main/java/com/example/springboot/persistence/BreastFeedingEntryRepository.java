package com.example.springboot.persistence;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.springboot.models.BreastFeedingEntry;

@Repository
public interface BreastFeedingEntryRepository extends JpaRepository<BreastFeedingEntry, Long> {
    @Query("SELECT p FROM BreastFeedingEntry p WHERE p.date BETWEEN :startDate AND :endDate ORDER BY p.date ASC, p.time ASC")
    List<BreastFeedingEntry> findByDateBetween(LocalDate startDate, LocalDate endDate);
}