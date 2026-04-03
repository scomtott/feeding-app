package com.example.springboot.homeassistant.websocket.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HaWsSubscribeEventsRequest(
    int id,
    String type,
    @JsonProperty("event_type") String eventType
) {
}
