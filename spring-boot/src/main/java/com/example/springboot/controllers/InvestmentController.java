package com.example.springboot.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.springboot.models.InvestmentHistoricalImportRequest;
import com.example.springboot.models.InvestmentImportResult;
import com.example.springboot.models.InvestmentPriceEntry;
import com.example.springboot.models.InvestmentPriceType;
import com.example.springboot.models.InvestmentSupportedFund;
import com.example.springboot.models.InvestmentUnitsEntry;
import com.example.springboot.models.InvestmentUnitsRequest;
import com.example.springboot.models.VanguardSupportedFundEntry;
import com.example.springboot.models.VanguardSupportedFundUpsertRequest;
import com.example.springboot.services.InvestmentUnitsService;
import com.example.springboot.services.VanguardSupportedFundService;
import com.example.springboot.services.VanguardInvestmentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final VanguardInvestmentService vanguardInvestmentService;
    private final InvestmentUnitsService investmentUnitsService;
    private final VanguardSupportedFundService vanguardSupportedFundService;

    @PostMapping("/vanguard/import-daily")
    public InvestmentImportResult importDaily() {
        return vanguardInvestmentService.importDaily();
    }

    @PostMapping("/vanguard/import-historical")
    public InvestmentImportResult importHistorical(@RequestBody InvestmentHistoricalImportRequest request) {
        return vanguardInvestmentService.importForRange(request.startDate(), request.endDate(), request.fundCodes());
    }

    @GetMapping("/prices")
    public List<InvestmentPriceEntry> getPrices(
        @RequestParam(required = false) String fundCode,
        @RequestParam(required = false) InvestmentPriceType priceType,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate
    ) {
        return vanguardInvestmentService.getPrices(fundCode, priceType, startDate, endDate);
    }

    @GetMapping("/vanguard/supported-funds")
    public List<InvestmentSupportedFund> getSupportedFunds() {
        return investmentUnitsService.getSupportedFunds();
    }

    @PostMapping("/vanguard/supported-funds")
    public VanguardSupportedFundEntry upsertSupportedFund(@RequestBody VanguardSupportedFundUpsertRequest request) {
        return vanguardSupportedFundService.upsertSupportedFund(request);
    }

    @GetMapping("/vanguard/supported-funds/all")
    public List<VanguardSupportedFundEntry> getAllSupportedFunds() {
        return vanguardSupportedFundService.getAllSupportedFunds();
    }

    @DeleteMapping("/vanguard/supported-funds/{code}")
    public void deleteSupportedFund(@PathVariable String code) {
        vanguardSupportedFundService.deleteSupportedFund(code);
    }

    @PostMapping("/holdings/units")
    public InvestmentUnitsEntry saveUnitsHeld(@RequestBody InvestmentUnitsRequest request) {
        return investmentUnitsService.saveUnitsHeld(request);
    }

    @GetMapping("/holdings/units")
    public List<InvestmentUnitsEntry> getUnitsHeld(
        @RequestParam(required = false) String fundCode,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate
    ) {
        return investmentUnitsService.getUnitsHeld(fundCode, startDate, endDate);
    }
}
