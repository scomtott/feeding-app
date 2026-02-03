package com.example.springboot.persistence;

import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.springboot.models.WeightEntry;

@Repository
public interface WeightRepository extends JpaRepository<WeightEntry, Long> {
}