package com.example.springboot.homeassistant.websocket.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsStateChangedEvent<T>(
    Integer id,
    String type,
    HaWsStateChangedEventPayload<T> event
) {
}
