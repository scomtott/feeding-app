package com.example.springboot.homeassistant.websocket.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsAuthInvalid(String type, String message) {
}
