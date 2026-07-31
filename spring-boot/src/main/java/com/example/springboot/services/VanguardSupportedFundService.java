package com.example.springboot.services;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

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

    private static final String DEFAULT_OPERATION_NAME = "PriceDetailsQuery";
    private static final String DEFAULT_QUERY =
        "query PriceDetailsQuery($portIds: [String!]!, $startDate: String!, $endDate: String!, $limit: Float) { "
            + "funds(portIds: $portIds) { pricingDetails { "
            + "navPrices(startDate: $startDate, endDate: $endDate, limit: $limit) { "
            + "items { price asOfDate currencyCode __typename } __typename } "
            + "marketPrices(startDate: $startDate, endDate: $endDate, limit: $limit) { "
            + "items { portId items { price asOfDate currencyCode __typename } __typename } __typename } "
            + "__typename } __typename } }";

    private final VanguardSupportedFundRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<InvestmentSupportedFund> getEnabledSupportedFunds() {
        return repository.findByEnabledTrueOrderByCodeAsc().stream()
            .map(entry -> new InvestmentSupportedFund(entry.getCode(), entry.getDisplayName(), entry.getIndexCode()))
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

        List<String> normalizedPortIds = normalizePortIds(request.portIds());
        if (normalizedPortIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "portIds is required");
        }

        String code = StringUtils.hasText(request.code())
            ? request.code().trim().toUpperCase(Locale.ROOT)
            : normalizedPortIds.get(0);
        String displayName = StringUtils.hasText(request.displayName())
            ? request.displayName().trim()
            : code;
        if (!StringUtils.hasText(request.indexCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "indexCode is required");
        }
        String indexCode = request.indexCode().trim().toUpperCase(Locale.ROOT);

        VanguardSupportedFundEntry existing = repository.findByCode(code).orElse(null);
        VanguardSupportedFundEntry entry = existing == null ? new VanguardSupportedFundEntry() : existing;

        entry.setCode(code);
        entry.setDisplayName(displayName);
        entry.setIndexCode(indexCode);
        entry.setEnabled(request.enabled() == null || request.enabled());
        entry.setStartDateVariable("startDate");
        entry.setEndDateVariable("endDate");
        entry.setOperationName(DEFAULT_OPERATION_NAME);
        entry.setQuery(DEFAULT_QUERY);
        entry.setVariablesJson(serializeVariables(buildVariables(
            request.variables(),
            normalizedPortIds,
            request.startDate(),
            request.endDate()
        )));
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
            entry.getIndexCode(),
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

    private List<String> normalizePortIds(List<String> rawPortIds) {
        if (rawPortIds == null) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String value : rawPortIds) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            normalized.add(value.trim().toUpperCase(Locale.ROOT));
        }
        return normalized;
    }

    private Map<String, Object> buildVariables(
        Map<String, Object> existingVariables,
        List<String> portIds,
        LocalDate startDate,
        LocalDate endDate
    ) {
        Map<String, Object> variables = existingVariables == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(existingVariables);

        variables.put("portIds", portIds);
        variables.putIfAbsent("limit", 0);
        if (startDate != null) {
            variables.put("startDate", startDate.toString());
        }
        if (endDate != null) {
            variables.put("endDate", endDate.toString());
        }
        return variables;
    }

    public record SupportedFundDefinition(
        String code,
        String displayName,
        String indexCode,
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
