package com.example.springboot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.springboot.models.LengthEntry;

@Repository
public interface LengthRepository extends JpaRepository<LengthEntry, Long> {
}