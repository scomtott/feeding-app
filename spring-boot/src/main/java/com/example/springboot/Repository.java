package com.example.springboot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface Repository extends JpaRepository<BreastFeedingEntry, Long> {
}