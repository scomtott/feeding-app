package com.example.springboot.homeassistant.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.springboot.homeassistant.persistence.entities.BathroomTimelinePoint;

@Repository
public interface BathroomTimelinePointRepository extends JpaRepository<BathroomTimelinePoint, Long> {

    List<BathroomTimelinePoint> findByTsUtcBetweenOrderByTsUtcAsc(Instant fromInclusive, Instant toInclusive);

    BathroomTimelinePoint findTopByOrderByTsUtcDesc();
}
