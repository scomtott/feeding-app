package com.example.springboot.persistence;

import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.springboot.models.WeightMeasurementEntry;

@Repository
public interface WeightMeasurementRepository extends JpaRepository<WeightMeasurementEntry, Long> {
}