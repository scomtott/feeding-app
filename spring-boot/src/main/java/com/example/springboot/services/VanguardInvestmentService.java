package com.example.springboot.services;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.example.springboot.config.VanguardInvestmentProperties;
import com.example.springboot.models.InvestmentImportResult;
import com.example.springboot.models.InvestmentPriceEntry;
import com.example.springboot.models.InvestmentPriceType;
import com.example.springboot.persistence.InvestmentPriceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VanguardInvestmentService {

    private static final String PROVIDER = "VANGUARD";

    private final VanguardInvestmentProperties properties;
    private final VanguardSupportedFundService supportedFundService;
    private final InvestmentPriceRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public InvestmentImportResult importDaily() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Math.max(1, properties.getLookbackDays()));
        return importForRange(startDate, endDate, null);
    }

    @Transactional
    public InvestmentImportResult importForRange(LocalDate startDate, LocalDate endDate, List<String> fundCodes) {
        if (!properties.isEnabled()) {
            return new InvestmentImportResult(startDate, endDate, 0, 0, 0, List.of("Vanguard import is disabled"));
        }

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        List<VanguardSupportedFundService.SupportedFundDefinition> configuredFunds =
            supportedFundService.getEnabledFundDefinitions();
        List<VanguardSupportedFundService.SupportedFundDefinition> fundsToProcess = filterFunds(configuredFunds, fundCodes);

        int inserted = 0;
        int updated = 0;
        List<String> messages = new ArrayList<>();

        for (VanguardSupportedFundService.SupportedFundDefinition fund : fundsToProcess) {
            List<ExtractedPrice> extractedPrices = fetchFundPrices(fund, startDate, endDate);
            int fundInserted = 0;
            int fundUpdated = 0;

            for (ExtractedPrice extractedPrice : extractedPrices) {
                UpsertOutcome outcome = upsertPrice(fund, extractedPrice);
                if (outcome == UpsertOutcome.INSERTED) {
                    inserted++;
                    fundInserted++;
                } else {
                    updated++;
                    fundUpdated++;
                }
            }

            messages.add(String.format(
                "Fund %s: %d prices fetched (%d inserted, %d updated)",
                fund.code(),
                extractedPrices.size(),
                fundInserted,
                fundUpdated
            ));
        }

        return new InvestmentImportResult(startDate, endDate, fundsToProcess.size(), inserted, updated, messages);
    }

    public List<InvestmentPriceEntry> getPrices(
        String fundCode,
        InvestmentPriceType priceType,
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (startDate == null) {
            startDate = LocalDate.of(2020, 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.of(2100, 1, 1);
        }

        if (StringUtils.hasText(fundCode) && priceType != null) {
            return repository.findByFundCodeAndPriceTypeAndPriceDateBetweenOrderByPriceDateAsc(
                fundCode,
                priceType,
                startDate,
                endDate
            );
        }

        if (StringUtils.hasText(fundCode)) {
            return repository.findByFundCodeAndPriceDateBetweenOrderByPriceDateAsc(fundCode, startDate, endDate);
        }

        return repository.findByPriceDateBetweenOrderByPriceDateAsc(startDate, endDate);
    }

    private List<VanguardSupportedFundService.SupportedFundDefinition> filterFunds(
        List<VanguardSupportedFundService.SupportedFundDefinition> configuredFunds,
        List<String> requestedFundCodes
    ) {
        if (requestedFundCodes == null || requestedFundCodes.isEmpty()) {
            return configuredFunds;
        }

        List<String> normalizedCodes = requestedFundCodes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(String::toUpperCase)
            .toList();

        return configuredFunds.stream()
            .filter(fund -> normalizedCodes.contains(fund.code().toUpperCase()))
            .toList();
    }

    private List<ExtractedPrice> fetchFundPrices(
        VanguardSupportedFundService.SupportedFundDefinition fund,
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (!StringUtils.hasText(fund.query())) {
            throw new IllegalStateException("Fund " + fund.code() + " has no request definition");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationName", fund.operationName());

        Map<String, Object> variables = new LinkedHashMap<>();
        if (fund.variables() != null) {
            variables.putAll(fund.variables());
        }
        variables.put(fund.startDateVariable(), startDate.toString());
        variables.put(fund.endDateVariable(), endDate.toString());
        payload.put("variables", variables);
        payload.put("query", fund.query());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        if (StringUtils.hasText(properties.getConsumerId())) {
            headers.set("x-consumer-id", properties.getConsumerId());
        }
        if (StringUtils.hasText(properties.getXsrfToken())) {
            headers.set("x-xsrf-token", properties.getXsrfToken());
            headers.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + properties.getXsrfToken());
        }

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(properties.getEndpoint(), requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Vanguard request failed for fund " + fund.code() + " with status " + response.getStatusCode());
        }

        return parsePricingDetailsResponse(fund, response.getBody());
    }

    private List<ExtractedPrice> parsePricingDetailsResponse(
        VanguardSupportedFundService.SupportedFundDefinition fund,
        String responseBody
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            List<ExtractedPrice> prices = new ArrayList<>();

            JsonNode navItems = readPath(root, fund.navItemsPath());
            if (navItems.isArray()) {
                for (JsonNode item : navItems) {
                    ExtractedPrice price = parseItem(item, InvestmentPriceType.NAV);
                    if (price != null) {
                        prices.add(price);
                    }
                }
            }

            JsonNode marketGroups = readPath(root, fund.marketGroupsPath());
            if (marketGroups.isArray()) {
                for (JsonNode marketGroup : marketGroups) {
                    JsonNode marketItems = marketGroup.path(fund.marketItemsField());
                    if (!marketItems.isArray()) {
                        continue;
                    }
                    for (JsonNode item : marketItems) {
                        ExtractedPrice price = parseItem(item, InvestmentPriceType.MARKET);
                        if (price != null) {
                            prices.add(price);
                        }
                    }
                }
            }

            return prices;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Unable to parse Vanguard response for fund " + fund.code(), ex);
        }
    }

    private JsonNode readPath(JsonNode root, String path) {
        if (!StringUtils.hasText(path)) {
            return objectMapper.nullNode();
        }

        JsonNode current = root;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (current == null || current.isMissingNode()) {
                return objectMapper.nullNode();
            }

            if (part.contains("[") && part.endsWith("]")) {
                String field = part.substring(0, part.indexOf('['));
                String indexPart = part.substring(part.indexOf('[') + 1, part.length() - 1);
                int index = Integer.parseInt(indexPart);
                current = current.path(field);
                if (!current.isArray() || current.size() <= index) {
                    return objectMapper.nullNode();
                }
                current = current.get(index);
            } else {
                current = current.path(part);
            }
        }

        return current == null ? objectMapper.nullNode() : current;
    }

    private ExtractedPrice parseItem(JsonNode item, InvestmentPriceType priceType) {
        if (item == null || item.isMissingNode()) {
            return null;
        }

        JsonNode dateNode = item.path("asOfDate");
        JsonNode priceNode = item.path("price");
        JsonNode currencyNode = item.path("currencyCode");

        if (dateNode.isMissingNode() || priceNode.isMissingNode()) {
            return null;
        }

        return new ExtractedPrice(
            LocalDate.parse(dateNode.asText()),
            priceNode.asDouble(),
            currencyNode.isMissingNode() ? "" : currencyNode.asText(),
            priceType
        );
    }

    private UpsertOutcome upsertPrice(VanguardSupportedFundService.SupportedFundDefinition fund, ExtractedPrice extractedPrice) {
        InvestmentPriceEntry existing = repository
            .findByFundCodeAndPriceTypeAndPriceDateAndCurrencyCode(
                fund.code(),
                extractedPrice.priceType,
                extractedPrice.priceDate,
                extractedPrice.currencyCode
            )
            .orElse(null);

        Instant now = Instant.now();
        if (existing == null) {
            InvestmentPriceEntry created = new InvestmentPriceEntry(
                fund.code(),
                fund.displayName(),
                PROVIDER,
                extractedPrice.priceType,
                extractedPrice.priceDate,
                extractedPrice.currencyCode,
                extractedPrice.price,
                now
            );
            repository.save(created);
            return UpsertOutcome.INSERTED;
        }

        existing.setFundName(fund.displayName());
        existing.setProvider(PROVIDER);
        existing.setPrice(extractedPrice.price);
        existing.setFetchedAt(now);
        repository.save(existing);
        return UpsertOutcome.UPDATED;
    }

    private record ExtractedPrice(
        LocalDate priceDate,
        double price,
        String currencyCode,
        InvestmentPriceType priceType
    ) {
    }

    private enum UpsertOutcome {
        INSERTED,
        UPDATED
    }
}
