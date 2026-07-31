package com.example.springboot.services;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.springboot.models.InvestmentSupportedFund;
import com.example.springboot.models.InvestmentUnitsEntry;
import com.example.springboot.models.InvestmentUnitsRequest;
import com.example.springboot.persistence.InvestmentUnitsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvestmentUnitsService {

    private final VanguardSupportedFundService vanguardSupportedFundService;
    private final InvestmentUnitsRepository investmentUnitsRepository;

    public List<InvestmentSupportedFund> getSupportedFunds() {
        return vanguardSupportedFundService.getEnabledSupportedFunds();
    }

    @Transactional
    public InvestmentUnitsEntry saveUnitsHeld(InvestmentUnitsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.date() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        if (!StringUtils.hasText(request.fundCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fundCode is required");
        }
        if (request.unitsHeld() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitsHeld cannot be negative");
        }

        String normalizedFundCode = request.fundCode().trim().toUpperCase(Locale.ROOT);
        InvestmentSupportedFund supportedFund = getSupportedFunds().stream()
            .filter(fund -> normalizedFundCode.equalsIgnoreCase(fund.code()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported fundCode: " + normalizedFundCode));

        InvestmentUnitsEntry existing = investmentUnitsRepository
            .findByFundCodeAndHoldingDate(normalizedFundCode, request.date())
            .orElse(null);

        Instant now = Instant.now();
        if (existing == null) {
            InvestmentUnitsEntry created = new InvestmentUnitsEntry(
                request.date(),
                normalizedFundCode,
                supportedFund.displayName(),
                request.unitsHeld(),
                now
            );
            return investmentUnitsRepository.save(created);
        }

        existing.setFundName(supportedFund.displayName());
        existing.setUnitsHeld(request.unitsHeld());
        existing.setUpdatedAt(now);
        return investmentUnitsRepository.save(existing);
    }

    public List<InvestmentUnitsEntry> getUnitsHeld(String fundCode, LocalDate startDate, LocalDate endDate) {
        LocalDate rangeStart = startDate == null ? LocalDate.of(2020, 1, 1) : startDate;
        LocalDate rangeEnd = endDate == null ? LocalDate.of(2100, 1, 1) : endDate;

        if (StringUtils.hasText(fundCode)) {
            return investmentUnitsRepository.findByFundCodeAndHoldingDateBetweenOrderByHoldingDateAsc(
                fundCode.trim().toUpperCase(Locale.ROOT),
                rangeStart,
                rangeEnd
            );
        }

        return investmentUnitsRepository.findByHoldingDateBetweenOrderByHoldingDateAsc(rangeStart, rangeEnd);
    }
}
