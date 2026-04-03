package com.example.springboot.homeassistant.websocket.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsStateChangedData<T>(
    @JsonProperty("entity_id") String entityId,
    @JsonProperty("old_state") T oldState,
    @JsonProperty("new_state") T newState
) {
}
