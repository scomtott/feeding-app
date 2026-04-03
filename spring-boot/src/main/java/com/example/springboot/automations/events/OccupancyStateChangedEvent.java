package com.example.springboot.automations.events;

public record OccupancyStateChangedEvent(
    String entityId,
    boolean occupied
) {
}
