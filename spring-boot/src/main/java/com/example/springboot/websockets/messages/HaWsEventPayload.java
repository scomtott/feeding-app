package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsEventPayload(
    @JsonProperty("event_type") String eventType,
    JsonNode data,
    String origin,
    @JsonProperty("time_fired") String timeFired
) {
}
