package com.example.springboot.homeassistant.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.springboot.homeassistant.properties.HomeAssistantProperties;
import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.models.BinarySensorEntity;
import com.example.springboot.homeassistant.models.SensorEntity;
import com.example.springboot.homeassistant.websocket.messages.*;
import com.example.springboot.homeassistant.services.LightBrightnessService;
import com.example.springboot.homeassistant.services.BinarySensorService;
import com.example.springboot.homeassistant.services.SensorService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class HomeAssistantWebSocketHandler extends TextWebSocketHandler {

    private final HomeAssistantProperties homeAssistantProperties;
    private final ObjectMapper objectMapper;
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);
    private final Set<String> subscribedSessionIds = ConcurrentHashMap.newKeySet();
    private final Map<String, DomainEventRoute<?>> domainEventRoutes;

    public HomeAssistantWebSocketHandler(
        HomeAssistantProperties properties,
        LightBrightnessService brightnessService,
        BinarySensorService binarySensorService,
        SensorService sensorService,
        ObjectMapper mapper
    ) {
        this.homeAssistantProperties = properties;
        this.objectMapper = mapper;
        this.domainEventRoutes = Map.of(
            "light",
            new DomainEventRoute<>(
                new TypeReference<HaWsStateChangedEvent<LightEntity>>() {
                },
                brightnessService::handleLightStateChanged
            ),
            "binary_sensor",
            new DomainEventRoute<>(
                new TypeReference<HaWsStateChangedEvent<BinarySensorEntity>>() {
                },
                binarySensorService::handleBinarySensorStateChanged
            ),
            "sensor",
            new DomainEventRoute<>(
                new TypeReference<HaWsStateChangedEvent<SensorEntity>>() {
                },
                sensorService::handleSensorStateChanged
            )
        );
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Connected to Home Assistant websocket: {}", session.getUri());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        HaWsEnvelope envelope;

        try {
            envelope = objectMapper.readValue(payload, HaWsEnvelope.class);
        } catch (RuntimeException e) {
            log.warn("Failed to parse Home Assistant websocket payload: {}", payload, e);
            return;
        }

        if (envelope.type() == null) {
            log.debug("Received websocket message without type: {}", payload);
            return;
        }

        switch (envelope.type()) {
            case "auth_required" -> handleAuthRequired(session, payload);
            case "auth_ok" -> handleAuthOk(session, payload);
            case "auth_invalid" -> handleAuthInvalid(payload);
            case "result" -> handleResult(payload);
            case "event" -> handleEvent(payload);
            default -> log.debug("Unhandled Home Assistant websocket message type: {} payload={}", envelope.type(), payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscribedSessionIds.remove(session.getId());
    }

    private void sendAuthMessage(WebSocketSession session) throws IOException {
        String token = homeAssistantProperties.getApiKey();
        if (token == null || token.isBlank()) {
            log.error("Cannot authenticate Home Assistant websocket: API key is missing");
            return;
        }

        HaWsAuthRequest authMessage = new HaWsAuthRequest("auth", token);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(authMessage)));
    }

    private void subscribeToStateChangedEvents(WebSocketSession session) throws IOException {
        if (!subscribedSessionIds.add(session.getId())) {
            return;
        }

        int messageId = messageIdCounter.incrementAndGet();
        HaWsSubscribeEventsRequest subscribeMessage = new HaWsSubscribeEventsRequest(messageId, "subscribe_events", "state_changed");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(subscribeMessage)));
        log.info("Subscribed to Home Assistant state_changed events (id={})", messageId);
    }

    private void handleAuthRequired(WebSocketSession session, String payload) throws IOException {
        HaWsAuthRequired message = objectMapper.readValue(payload, HaWsAuthRequired.class);
        log.info("Home Assistant websocket auth required (ha_version={})", message.haVersion());
        sendAuthMessage(session);
    }

    private void handleAuthOk(WebSocketSession session, String payload) throws IOException {
        HaWsAuthOk message = objectMapper.readValue(payload, HaWsAuthOk.class);
        log.info("Authenticated with Home Assistant websocket (ha_version={})", message.haVersion());
        subscribeToStateChangedEvents(session);
    }

    private void handleAuthInvalid(String payload) throws IOException {
        HaWsAuthInvalid message = objectMapper.readValue(payload, HaWsAuthInvalid.class);
        log.error("Home Assistant websocket authentication failed: {}", message.message());
    }

    private void handleResult(String payload) throws IOException {
        HaWsResult result = objectMapper.readValue(payload, HaWsResult.class);
        if (Boolean.TRUE.equals(result.success())) {
            log.debug("Home Assistant websocket result succeeded (id={})", result.id());
            return;
        }

        if (result.error() != null) {
            log.warn(
                "Home Assistant websocket result failed (id={}): code={}, message={}",
                result.id(),
                result.error().code(),
                result.error().message()
            );
            return;
        }

        log.warn("Home Assistant websocket result failed (id={}) with unknown error payload", result.id());
    }

    private void handleEvent(String payload) throws IOException {
        HaWsEvent eventMessage = objectMapper.readValue(payload, HaWsEvent.class);

        if (eventMessage.event() == null) {
            log.debug("Home Assistant websocket event without payload: {}", payload);
            return;
        }

        if (!"state_changed".equals(eventMessage.event().eventType())) {
            log.debug("Ignoring Home Assistant websocket event type={}", eventMessage.event().eventType());
            return;
        }

        String entityId = eventMessage.event().data() == null || eventMessage.event().data().get("entity_id") == null
            ? "unknown"
            : eventMessage.event().data().get("entity_id").asText("unknown");

        String domain = parseDomainFromEntityId(entityId);
        DomainEventRoute<?> route = domainEventRoutes.get(domain);

        if (route == null) {
            log.debug("Unhandled Home Assistant domain: {}", domain);
            return;
        }

        route.handle(payload, objectMapper);

        log.debug("Home Assistant state_changed event for {}", entityId == null ? "unknown" : entityId);

        if (entityId != null && entityId.contains("person.tom")) {
            log.info("Person Tom state changed: {}", payload);
        }
    }

    private String parseDomainFromEntityId(String entityId) {
        if (entityId == null || !entityId.contains(".")) {
            return "unknown";
        }
        return entityId.substring(0, entityId.indexOf('.'));
    }

    private static final class DomainEventRoute<T> {
        private final TypeReference<HaWsStateChangedEvent<T>> typeReference;
        private final Consumer<HaWsStateChangedEvent<T>> consumer;

        private DomainEventRoute(TypeReference<HaWsStateChangedEvent<T>> typeReference, Consumer<HaWsStateChangedEvent<T>> consumer) {
            this.typeReference = typeReference;
            this.consumer = consumer;
        }

        private void handle(String payload, ObjectMapper objectMapper) throws IOException {
            HaWsStateChangedEvent<T> event = objectMapper.readValue(payload, typeReference);
            consumer.accept(event);
        }
    }
}