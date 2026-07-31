package com.example.springboot.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.springboot.models.VanguardSupportedFundEntry;

@Repository
public interface VanguardSupportedFundRepository extends JpaRepository<VanguardSupportedFundEntry, Long> {

    Optional<VanguardSupportedFundEntry> findByCode(String code);

    List<VanguardSupportedFundEntry> findByEnabledTrueOrderByCodeAsc();

    List<VanguardSupportedFundEntry> findAllByOrderByCodeAsc();
}
