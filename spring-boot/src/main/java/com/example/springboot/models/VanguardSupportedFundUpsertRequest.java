package com.example.springboot.models;

import java.util.Map;

public record VanguardSupportedFundUpsertRequest(
    String code,
    String displayName,
    Boolean enabled,
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
