package com.example.springboot.models;

import java.util.Map;
import java.util.List;
import java.time.LocalDate;

public record VanguardSupportedFundUpsertRequest(
    String code,
    String displayName,
    String indexCode,
    Boolean enabled,
    LocalDate startDate,
    LocalDate endDate,
    List<String> portIds,
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
