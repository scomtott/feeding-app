package com.example.springboot.persistence;

import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.springboot.models.BreastFeedingEntry;

@Repository
public interface BreastFeedingEntryRepository extends JpaRepository<BreastFeedingEntry, Long> {
}