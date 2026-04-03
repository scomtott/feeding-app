package com.example.springboot.homeassistant.automations.events;

public record OccupancyStateChangedEvent(
    String entityId,
    boolean occupied
) {
}
