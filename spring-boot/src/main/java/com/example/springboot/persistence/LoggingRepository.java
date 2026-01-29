package com.example.springboot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.springboot.models.LoggingEntry;

@Repository
public interface LoggingRepository extends JpaRepository<LoggingEntry, Long> {
}