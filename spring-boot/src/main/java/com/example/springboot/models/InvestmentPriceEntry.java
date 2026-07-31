package com.example.springboot.models;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity(name = "InvestmentPriceEntry")
@Table(
    indexes = {
        @Index(name = "idx_investment_price_fund_date", columnList = "fundCode,priceDate"),
        @Index(name = "idx_investment_price_date", columnList = "priceDate")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_investment_price_fund_type_date_currency",
            columnNames = {"fundCode", "priceType", "priceDate", "currencyCode"}
        )
    }
)
public class InvestmentPriceEntry {

    @Id
    @GeneratedValue
    private Long id;

    private String fundCode;

    private String fundName;

    private String provider;

    @Enumerated(EnumType.STRING)
    private InvestmentPriceType priceType;

    private LocalDate priceDate;

    private String currencyCode;

    private double price;

    private Instant fetchedAt;

    public InvestmentPriceEntry() {
    }

    public InvestmentPriceEntry(
        String fundCode,
        String fundName,
        String provider,
        InvestmentPriceType priceType,
        LocalDate priceDate,
        String currencyCode,
        double price,
        Instant fetchedAt
    ) {
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.provider = provider;
        this.priceType = priceType;
        this.priceDate = priceDate;
        this.currencyCode = currencyCode;
        this.price = price;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getFundCode() {
        return fundCode;
    }

    public void setFundCode(String fundCode) {
        this.fundCode = fundCode;
    }

    public String getFundName() {
        return fundName;
    }

    public void setFundName(String fundName) {
        this.fundName = fundName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public InvestmentPriceType getPriceType() {
        return priceType;
    }

    public void setPriceType(InvestmentPriceType priceType) {
        this.priceType = priceType;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(LocalDate priceDate) {
        this.priceDate = priceDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
