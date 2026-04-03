package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsStateChangedEvent<T>(
    Integer id,
    String type,
    HaWsStateChangedEventPayload<T> event
) {
}
