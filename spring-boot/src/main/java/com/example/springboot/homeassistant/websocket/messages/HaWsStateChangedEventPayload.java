package com.example.springboot.homeassistant.websocket.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsStateChangedEventPayload<T>(
    @JsonProperty("event_type") String eventType,
    HaWsStateChangedData<T> data,
    String origin,
    @JsonProperty("time_fired") String timeFired
) {
}
