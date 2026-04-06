package com.example.springboot.homeassistant.services;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class BrightnessAutomationExclusionRegistry {

    private static final Set<String> EXCLUDED_ENTITY_IDS = Arrays.stream(new String[] {
        // Add excluded light entity IDs here.
        "light.nursery_bedside_light",
        "light.all_lights",
        "light.gledopto_light"
    })
        .map(BrightnessAutomationExclusionRegistry::normalizeLightEntityId)
        .collect(Collectors.toUnmodifiableSet());

    private BrightnessAutomationExclusionRegistry() {
    }

    public static boolean isExcluded(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return false;
        }
        return EXCLUDED_ENTITY_IDS.contains(normalizeLightEntityId(entityId));
    }

    public static Set<String> getExcludedEntityIds() {
        return EXCLUDED_ENTITY_IDS;
    }

    private static String normalizeLightEntityId(String entityId) {
        String trimmed = entityId.trim().toLowerCase();
        return trimmed.startsWith("light.") ? trimmed : "light." + trimmed;
    }
}
