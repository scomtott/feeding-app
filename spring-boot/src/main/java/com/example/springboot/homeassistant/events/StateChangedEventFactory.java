package com.example.springboot.homeassistant.events;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.springboot.homeassistant.models.BinarySensorEntity;
import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a raw {@code state_changed} websocket payload into the typed {@link StateChangedEvent} that
 * services subscribe to. This is the only place that knows the Home Assistant wire format, so the
 * services can consume the domain vocabulary instead.
 */
@Component
@RequiredArgsConstructor
public class StateChangedEventFactory {

    private static final Set<String> SUPPORTED_DOMAINS = Set.of(
        LightStateChangedEvent.DOMAIN,
        BinarySensorStateChangedEvent.DOMAIN
    );

    private static final TypeReference<HaWsStateChangedEvent<LightEntity>> LIGHT_EVENT_TYPE =
        new TypeReference<>() {
        };

    private static final TypeReference<HaWsStateChangedEvent<BinarySensorEntity>> BINARY_SENSOR_EVENT_TYPE =
        new TypeReference<>() {
        };

    private final ObjectMapper objectMapper;

    /**
     * Whether this domain has a typed event. Gates the work submitted to the event executor: Home
     * Assistant subscribes to every {@code state_changed} in the house, and without this check each
     * {@code person.*} or {@code switch.*} toggle would occupy an {@code ha-event-*} thread.
     */
    public boolean supports(String domain) {
        return SUPPORTED_DOMAINS.contains(domain);
    }

    /**
     * Parses {@code payload} into the event for {@code domain}, or empty when the domain has no
     * event type. Throws if the payload cannot be parsed — callers run this off the websocket I/O
     * thread and log the failure.
     */
    public Optional<StateChangedEvent> create(String payload, String entityId, String domain) {
        return switch (domain) {
            case LightStateChangedEvent.DOMAIN -> Optional.of(new LightStateChangedEvent(
                entityId,
                oldState(parse(payload, LIGHT_EVENT_TYPE)),
                newState(parse(payload, LIGHT_EVENT_TYPE))
            ));
            case BinarySensorStateChangedEvent.DOMAIN -> Optional.of(new BinarySensorStateChangedEvent(
                entityId,
                oldState(parse(payload, BINARY_SENSOR_EVENT_TYPE)),
                newState(parse(payload, BINARY_SENSOR_EVENT_TYPE))
            ));
            default -> Optional.empty();
        };
    }

    private <T> HaWsStateChangedEvent<T> parse(String payload, TypeReference<HaWsStateChangedEvent<T>> eventType) {
        return objectMapper.readValue(payload, eventType);
    }

    /** Null when the envelope, payload or state is absent — services rely on this rather than guarding. */
    private static <T> T oldState(HaWsStateChangedEvent<T> event) {
        return event == null || event.event() == null || event.event().data() == null
            ? null
            : event.event().data().oldState();
    }

    private static <T> T newState(HaWsStateChangedEvent<T> event) {
        return event == null || event.event() == null || event.event().data() == null
            ? null
            : event.event().data().newState();
    }
}
