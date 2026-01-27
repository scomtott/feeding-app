package com.example.springboot.persistence;

import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.springboot.models.PumpingEntry;

@Repository
public interface PumpingEntryRepository extends JpaRepository<PumpingEntry, Long> {
}