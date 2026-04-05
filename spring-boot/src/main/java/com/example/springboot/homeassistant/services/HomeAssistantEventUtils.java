package com.example.springboot.homeassistant.services;

import java.time.Instant;

import tools.jackson.databind.ObjectMapper;

public final class HomeAssistantEventUtils {

    private HomeAssistantEventUtils() {
    }

    public static Instant parseEventTimestamp(String preferred, String fallback) {
        String candidate = preferred != null ? preferred : fallback;
        if (candidate == null) {
            return Instant.now();
        }

        try {
            return Instant.parse(candidate);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    public static String serializeEventSilently(ObjectMapper objectMapper, Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
