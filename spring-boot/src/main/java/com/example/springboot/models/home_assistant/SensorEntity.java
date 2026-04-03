package com.example.springboot.models.home_assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SensorEntity(
    @JsonProperty("entity_id") String entityId,
    String state,
    Attributes attributes,
    @JsonProperty("last_changed") String lastChanged,
    @JsonProperty("last_updated") String lastUpdated
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(
        @JsonProperty("friendly_name") String friendlyName,
        @JsonProperty("device_class") String deviceClass,
        @JsonProperty("unit_of_measurement") String unitOfMeasurement
    ) {
    }

    public Double getNumericalValue() {
        if (state == null) {
            return null;
        }
        try {
            return Double.valueOf(state);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
