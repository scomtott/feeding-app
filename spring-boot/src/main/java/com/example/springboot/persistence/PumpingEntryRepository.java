package com.example.springboot.persistence;

import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.springboot.models.PumpingEntry;

@Repository
public interface PumpingEntryRepository extends JpaRepository<PumpingEntry, Long> {
    @Query("SELECT p FROM PumpingEntry p WHERE p.date BETWEEN :startDate AND :endDate ORDER BY p.date ASC, p.time ASC")
    List<PumpingEntry> findByDateBetween(LocalDate startDate, LocalDate endDate);
}