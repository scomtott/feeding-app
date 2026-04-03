package com.example.springboot.homeassistant.websocket.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsAuthOk(
    String type,
    @JsonProperty("ha_version") String haVersion
) {
}
