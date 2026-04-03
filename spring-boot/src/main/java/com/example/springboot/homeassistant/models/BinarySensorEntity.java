package com.example.springboot.homeassistant.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinarySensorEntity(
    @JsonProperty("entity_id") String entityId,
    @JsonProperty("state") String state,
    Attributes attributes,
    @JsonProperty("last_changed") String lastChanged,
    @JsonProperty("last_updated") String lastUpdated
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(
        @JsonProperty("friendly_name") String friendlyName,
        @JsonProperty("device_class") String deviceClass
    ) {
    }

    public boolean isOn() {
        return "on".equalsIgnoreCase(state);
    }
}
