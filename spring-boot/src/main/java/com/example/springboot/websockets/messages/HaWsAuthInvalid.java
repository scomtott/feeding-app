package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsAuthInvalid(String type, String message) {
}
