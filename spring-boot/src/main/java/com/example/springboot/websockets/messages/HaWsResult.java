package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HaWsResult(
    Integer id,
    String type,
    Boolean success,
    JsonNode result,
    HaWsResultError error
) {
}
