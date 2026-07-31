package com.example.springboot.services;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.springboot.models.InvestmentSupportedFund;
import com.example.springboot.models.VanguardSupportedFundEntry;
import com.example.springboot.models.VanguardSupportedFundUpsertRequest;
import com.example.springboot.persistence.VanguardSupportedFundRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VanguardSupportedFundService {

    private final VanguardSupportedFundRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<InvestmentSupportedFund> getEnabledSupportedFunds() {
        return repository.findByEnabledTrueOrderByCodeAsc().stream()
            .map(entry -> new InvestmentSupportedFund(entry.getCode(), entry.getDisplayName()))
            .toList();
    }

    public List<SupportedFundDefinition> getEnabledFundDefinitions() {
        return repository.findByEnabledTrueOrderByCodeAsc().stream()
            .map(this::toDefinition)
            .toList();
    }

    public List<VanguardSupportedFundEntry> getAllSupportedFunds() {
        return repository.findAllByOrderByCodeAsc();
    }

    @Transactional
    public VanguardSupportedFundEntry upsertSupportedFund(VanguardSupportedFundUpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (!StringUtils.hasText(request.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
        }
        if (!StringUtils.hasText(request.displayName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
        }
        if (!StringUtils.hasText(request.operationName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "operationName is required");
        }
        if (!StringUtils.hasText(request.query())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }

        String code = request.code().trim().toUpperCase(Locale.ROOT);
        VanguardSupportedFundEntry existing = repository.findByCode(code).orElse(null);
        VanguardSupportedFundEntry entry = existing == null ? new VanguardSupportedFundEntry() : existing;

        entry.setCode(code);
        entry.setDisplayName(request.displayName().trim());
        entry.setEnabled(request.enabled() == null || request.enabled());
        entry.setStartDateVariable(defaultIfBlank(request.startDateVariable(), "startDate"));
        entry.setEndDateVariable(defaultIfBlank(request.endDateVariable(), "endDate"));
        entry.setOperationName(request.operationName().trim());
        entry.setQuery(request.query());
        entry.setVariablesJson(serializeVariables(request.variables()));
        entry.setNavItemsPath(defaultIfBlank(request.navItemsPath(), "data.funds[0].pricingDetails.navPrices.items"));
        entry.setMarketGroupsPath(defaultIfBlank(request.marketGroupsPath(), "data.funds[0].pricingDetails.marketPrices.items"));
        entry.setMarketItemsField(defaultIfBlank(request.marketItemsField(), "items"));

        return repository.save(entry);
    }

    @Transactional
    public void deleteSupportedFund(String code) {
        if (!StringUtils.hasText(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
        }

        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        VanguardSupportedFundEntry existing = repository.findByCode(normalizedCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supported fund not found: " + normalizedCode));

        repository.delete(existing);
    }

    private SupportedFundDefinition toDefinition(VanguardSupportedFundEntry entry) {
        return new SupportedFundDefinition(
            entry.getCode(),
            entry.getDisplayName(),
            entry.getStartDateVariable(),
            entry.getEndDateVariable(),
            entry.getOperationName(),
            entry.getQuery(),
            parseVariables(entry.getVariablesJson()),
            entry.getNavItemsPath(),
            entry.getMarketGroupsPath(),
            entry.getMarketItemsField()
        );
    }

    private Map<String, Object> parseVariables(String variablesJson) {
        try {
            if (!StringUtils.hasText(variablesJson)) {
                return Map.of();
            }
            return objectMapper.readValue(variablesJson, new TypeReference<Map<String, Object>>() {});
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid variablesJson stored for supported fund", ex);
        }
    }

    private String serializeVariables(Map<String, Object> variables) {
        try {
            Map<String, Object> safeVariables = variables == null ? Map.of() : new LinkedHashMap<>(variables);
            return objectMapper.writeValueAsString(safeVariables);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "variables must be valid JSON object");
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    public record SupportedFundDefinition(
        String code,
        String displayName,
        String startDateVariable,
        String endDateVariable,
        String operationName,
        String query,
        Map<String, Object> variables,
        String navItemsPath,
        String marketGroupsPath,
        String marketItemsField
    ) {
    }
}
