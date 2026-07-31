package com.example.springboot.models;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity(name = "InvestmentUnitsEntry")
@Table(
    indexes = {
        @Index(name = "idx_investment_units_fund_date", columnList = "fundCode,holdingDate"),
        @Index(name = "idx_investment_units_date", columnList = "holdingDate")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_investment_units_fund_date", columnNames = {"fundCode", "holdingDate"})
    }
)
public class InvestmentUnitsEntry {

    @Id
    @GeneratedValue
    private Long id;

    private LocalDate holdingDate;

    private String fundCode;

    private String fundName;

    private double unitsHeld;

    private Instant updatedAt;

    public InvestmentUnitsEntry() {
    }

    public InvestmentUnitsEntry(LocalDate holdingDate, String fundCode, String fundName, double unitsHeld, Instant updatedAt) {
        this.holdingDate = holdingDate;
        this.fundCode = fundCode;
        this.fundName = fundName;
        this.unitsHeld = unitsHeld;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getHoldingDate() {
        return holdingDate;
    }

    public void setHoldingDate(LocalDate holdingDate) {
        this.holdingDate = holdingDate;
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

    public double getUnitsHeld() {
        return unitsHeld;
    }

    public void setUnitsHeld(double unitsHeld) {
        this.unitsHeld = unitsHeld;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
