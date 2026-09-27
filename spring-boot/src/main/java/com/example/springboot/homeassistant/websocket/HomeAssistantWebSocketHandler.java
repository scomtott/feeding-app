package com.example.springboot.homeassistant.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.springboot.homeassistant.events.StateChangedEventFactory;
import com.example.springboot.homeassistant.properties.HomeAssistantProperties;
import com.example.springboot.homeassistant.websocket.messages.*;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * The Home Assistant websocket listener.
 *
 * <p>Its only job is transport: authenticate, subscribe to {@code state_changed}, and turn each
 * inbound payload into a typed {@code StateChangedEvent} published on the Spring event bus. Which
 * services react, and in what order, is decided in
 * {@link com.example.springboot.homeassistant.HomeAssistantEventSubscriptions}.
 */
@Component
@Slf4j
public class HomeAssistantWebSocketHandler extends TextWebSocketHandler {

    private final HomeAssistantProperties homeAssistantProperties;
    private final StateChangedEventFactory stateChangedEventFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final AtomicInteger messageIdCounter = new AtomicInteger(0);
    private final Set<String> subscribedSessionIds = ConcurrentHashMap.newKeySet();
    private final Executor homeAssistantEventExecutor;

    public HomeAssistantWebSocketHandler(
        HomeAssistantProperties properties,
        StateChangedEventFactory stateChangedEventFactory,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper mapper,
        @Qualifier("homeAssistantEventExecutor")
        Executor homeAssistantEventExecutor
    ) {
        this.homeAssistantProperties = properties;
        this.stateChangedEventFactory = stateChangedEventFactory;
        this.eventPublisher = eventPublisher;
        this.objectMapper = mapper;
        this.homeAssistantEventExecutor = homeAssistantEventExecutor;
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

    private void handleEvent(String payload) {
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
        if (!stateChangedEventFactory.supports(domain)) {
            log.debug("Unhandled Home Assistant domain: {}", domain);
            return;
        }

        // Publishing must stay inside the executor. Subscribers include synchronous work — Quartz
        // cancel/schedule, HTTP calls, the file logger — which would otherwise run on the websocket
        // I/O thread.
        homeAssistantEventExecutor.execute(() -> {
            try {
                stateChangedEventFactory.create(payload, entityId, domain)
                    .ifPresent(eventPublisher::publishEvent);
            } catch (Exception e) {
                log.warn("Failed to process Home Assistant state_changed event asynchronously for {}", entityId, e);
            }
        });

        log.debug("Home Assistant state_changed event for {}", entityId);

        if (entityId.contains("person.tom")) {
            log.info("Person Tom state changed: {}", payload);
        }
    }

    private String parseDomainFromEntityId(String entityId) {
        if (entityId == null || !entityId.contains(".")) {
            return "unknown";
        }
        return entityId.substring(0, entityId.indexOf('.'));
    }
}
