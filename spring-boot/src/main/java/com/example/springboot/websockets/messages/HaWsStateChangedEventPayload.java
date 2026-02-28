package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsStateChangedEventPayload(
    @JsonProperty("event_type") String eventType,
    HaWsStateChangedData data,
    String origin,
    @JsonProperty("time_fired") String timeFired
) {
}
