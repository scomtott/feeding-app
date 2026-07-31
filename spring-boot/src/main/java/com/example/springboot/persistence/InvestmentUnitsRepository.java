package com.example.springboot.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.springboot.models.InvestmentUnitsEntry;

@Repository
public interface InvestmentUnitsRepository extends JpaRepository<InvestmentUnitsEntry, Long> {

    Optional<InvestmentUnitsEntry> findByFundCodeAndHoldingDate(String fundCode, LocalDate holdingDate);

    List<InvestmentUnitsEntry> findByFundCodeAndHoldingDateBetweenOrderByHoldingDateAsc(
        String fundCode,
        LocalDate startDate,
        LocalDate endDate
    );

    List<InvestmentUnitsEntry> findByHoldingDateBetweenOrderByHoldingDateAsc(LocalDate startDate, LocalDate endDate);
}
