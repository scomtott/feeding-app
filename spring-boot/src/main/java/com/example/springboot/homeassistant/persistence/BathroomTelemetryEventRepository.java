package com.example.springboot.homeassistant.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.springboot.homeassistant.persistence.entities.BathroomTelemetryEvent;

@Repository
public interface BathroomTelemetryEventRepository extends JpaRepository<BathroomTelemetryEvent, Long> {

    List<BathroomTelemetryEvent> findByTsUtcBetweenOrderByTsUtcAsc(Instant fromInclusive, Instant toInclusive);

    List<BathroomTelemetryEvent> findByEntityIdOrderByTsUtcAsc(String entityId);
}
