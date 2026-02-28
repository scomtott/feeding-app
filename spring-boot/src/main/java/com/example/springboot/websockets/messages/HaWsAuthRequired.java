package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsAuthRequired(
    String type,
    @JsonProperty("ha_version") String haVersion
) {
}
