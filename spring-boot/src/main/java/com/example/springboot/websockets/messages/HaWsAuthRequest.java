package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HaWsAuthRequest(
    String type,
    @JsonProperty("access_token") String accessToken
) {
}
