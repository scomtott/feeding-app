package com.example.springboot.websockets.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

public final class HomeAssistantWsMessages {

    private HomeAssistantWsMessages() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(Integer id, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthRequiredMessage(
        String type,
        @JsonProperty("ha_version") String haVersion
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthOkMessage(
        String type,
        @JsonProperty("ha_version") String haVersion
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthInvalidMessage(
        String type,
        String message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultMessage(
        Integer id,
        String type,
        Boolean success,
        JsonNode result,
        JsonNode error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventMessage(
        Integer id,
        String type,
        EventPayload event
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventPayload(
        @JsonProperty("event_type") String eventType,
        JsonNode data,
        String origin,
        @JsonProperty("time_fired") String timeFired
    ) {
    }

    public record AuthMessage(
        String type,
        @JsonProperty("access_token") String accessToken
    ) {
    }

    public record SubscribeEventsMessage(
        int id,
        String type,
        @JsonProperty("event_type") String eventType
    ) {
    }
}
