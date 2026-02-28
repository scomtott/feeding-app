package com.example.springboot.websockets.messages;

import com.example.springboot.models.home_assistant.LightEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsStateChangedData(
    @JsonProperty("entity_id") String entityId,
    @JsonProperty("old_state") LightEntity oldState,
    @JsonProperty("new_state") LightEntity newState
) {
}
