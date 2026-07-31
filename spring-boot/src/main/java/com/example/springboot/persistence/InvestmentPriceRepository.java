package com.example.springboot.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.springboot.models.InvestmentPriceEntry;
import com.example.springboot.models.InvestmentPriceType;

@Repository
public interface InvestmentPriceRepository extends JpaRepository<InvestmentPriceEntry, Long> {

    Optional<InvestmentPriceEntry> findByFundCodeAndPriceTypeAndPriceDateAndCurrencyCode(
        String fundCode,
        InvestmentPriceType priceType,
        LocalDate priceDate,
        String currencyCode
    );

    List<InvestmentPriceEntry> findByFundCodeAndPriceDateBetweenOrderByPriceDateAsc(
        String fundCode,
        LocalDate startDate,
        LocalDate endDate
    );

    List<InvestmentPriceEntry> findByPriceDateBetweenOrderByPriceDateAsc(LocalDate startDate, LocalDate endDate);

    List<InvestmentPriceEntry> findByFundCodeAndPriceTypeAndPriceDateBetweenOrderByPriceDateAsc(
        String fundCode,
        InvestmentPriceType priceType,
        LocalDate startDate,
        LocalDate endDate
    );
}
