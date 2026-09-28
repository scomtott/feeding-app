package com.example.springboot.homeassistant.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.example.springboot.homeassistant.events.BinarySensorStateChangedEvent;
import com.example.springboot.homeassistant.events.LightStateChangedEvent;
import com.example.springboot.homeassistant.events.StateChangedEventFactory;
import com.example.springboot.homeassistant.properties.HomeAssistantProperties;

import tools.jackson.databind.json.JsonMapper;

/**
 * The handler is constructed with {@code Runnable::run} as its executor, so the submission to
 * {@code homeAssistantEventExecutor} runs inline and the assertions are deterministic.
 */
class HomeAssistantWebSocketHandlerTest {

    private static final String LIGHT_ENTITY_ID = "light.bathroom_1";
    private static final String SENSOR_ENTITY_ID = "binary_sensor.bathroom_motion_sensor_occupancy";

    private CapturingPublisher publisher;
    private HomeAssistantWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        publisher = new CapturingPublisher();
        JsonMapper objectMapper = JsonMapper.builder().build();

        handler = new HomeAssistantWebSocketHandler(
            new HomeAssistantProperties(),
            new StateChangedEventFactory(objectMapper),
            publisher,
            objectMapper,
            Runnable::run
        );
    }

    @Test
    void publishesATypedLightEventForALightStateChange() throws Exception {
        handler.handleTextMessage(session(), message(lightPayload()));

        LightStateChangedEvent event = assertInstanceOf(LightStateChangedEvent.class, solePublish());
        assertEquals(LIGHT_ENTITY_ID, event.entityId());
        assertEquals("light", event.domain());
        assertEquals("off", event.oldState().state());
        assertEquals("on", event.newState().state());
        assertEquals(200, event.newState().attributes().brightness());
    }

    @Test
    void publishesATypedBinarySensorEventForABinarySensorStateChange() throws Exception {
        handler.handleTextMessage(session(), message(binarySensorPayload()));

        BinarySensorStateChangedEvent event = assertInstanceOf(BinarySensorStateChangedEvent.class, solePublish());
        assertEquals(SENSOR_ENTITY_ID, event.entityId());
        assertEquals("occupancy", event.deviceClass());
        assertTrue(event.newState().isOn());
    }

    @Test
    void publishesNothingForAnUnsupportedDomain() throws Exception {
        handler.handleTextMessage(session(), message(payloadFor("sensor.bathroom_motion_sensor_illuminance", "state_changed")));

        assertTrue(publisher.events.isEmpty(), "expected no publish, got: " + publisher.events);
    }

    @Test
    void publishesNothingForANonStateChangedEventType() throws Exception {
        handler.handleTextMessage(session(), message(payloadFor(LIGHT_ENTITY_ID, "call_service")));

        assertTrue(publisher.events.isEmpty(), "expected no publish, got: " + publisher.events);
    }

    @Test
    void doesNotThrowOnAMalformedPayload() {
        assertDoesNotThrow(() -> handler.handleTextMessage(session(), message("this is not json")));
        assertTrue(publisher.events.isEmpty());
    }

    @Test
    void doesNotThrowWhenTheEventCarriesNoData() {
        assertDoesNotThrow(() -> handler.handleTextMessage(
            session(),
            message("""
                {"id": 1, "type": "event", "event": {"event_type": "state_changed"}}
                """)
        ));
        assertTrue(publisher.events.isEmpty());
    }

    // --- connection state and keepalive -------------------------------------------------

    @Test
    void isConnectedOnlyWhileASessionIsOpen() {
        assertFalse(handler.isConnected(), "no session established yet");

        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);
        assertTrue(handler.isConnected());

        when(session.isOpen()).thenReturn(false);
        assertFalse(handler.isConnected(), "the socket went away underneath us");
    }

    @Test
    void isSubscribedOnlyAfterAuthOk() throws Exception {
        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);
        assertFalse(handler.isSubscribed(), "connected is not the same as subscribed to events");

        handler.handleTextMessage(session, message("{\"type\": \"auth_ok\", \"ha_version\": \"2026.5.3\"}"));

        assertTrue(handler.isSubscribed());
    }

    @Test
    void sendsAPingCarryingAnId() throws Exception {
        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);

        assertTrue(handler.sendPing());
        assertTrue(handler.isPingOutstanding());

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        assertTrue(sent.getValue().getPayload().contains("\"type\":\"ping\""), sent.getValue().getPayload());
        assertTrue(pingIdOf(sent.getValue()) > 0);
    }

    @Test
    void aPongCarryingThePingIdClearsIt() throws Exception {
        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);
        handler.sendPing();

        handler.handleTextMessage(session, message(pongFor(pingIdSentOn(session))));

        assertFalse(handler.isPingOutstanding());
    }

    @Test
    void aPongCarryingAnUnexpectedIdLeavesThePingOutstanding() throws Exception {
        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);
        handler.sendPing();
        int sentId = pingIdSentOn(session);

        handler.handleTextMessage(session, message(pongFor(sentId + 1000)));

        assertTrue(handler.isPingOutstanding(), "an unrelated pong must not count as the reply");
    }

    @Test
    void doesNotPingWithoutAnOpenSession() {
        assertFalse(handler.sendPing());
        assertFalse(handler.isPingOutstanding());
    }

    @Test
    void closingTheSessionIsLoggedAsAWarning() {
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(HomeAssistantWebSocketHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
        try {
            WebSocketSession session = openSession();
            handler.afterConnectionEstablished(session);
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            assertTrue(
                appender.list.stream().anyMatch(
                    event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("websocket closed")
                ),
                "a dropped subscription used to be silent; it has to announce itself now"
            );
        } finally {
            handlerLogger.detachAppender(appender);
        }
    }

    @Test
    void closingClearsTheSessionAndAnyOutstandingPing() throws Exception {
        WebSocketSession session = openSession();
        handler.afterConnectionEstablished(session);
        handler.sendPing();

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertFalse(handler.isConnected());
        assertFalse(handler.isPingOutstanding());
    }

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session-1");
        return session;
    }

    private String pongFor(int id) {
        return "{\"id\": " + id + ", \"type\": \"pong\"}";
    }

    /** Reads the id the handler actually used, rather than assuming the counter's value. */
    private int pingIdSentOn(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        return pingIdOf(sent.getValue());
    }

    private int pingIdOf(TextMessage sent) {
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(sent.getPayload());
        assertTrue(matcher.find(), "no id in ping payload: " + sent.getPayload());
        return Integer.parseInt(matcher.group(1));
    }

    private Object solePublish() {
        assertEquals(1, publisher.events.size(), "expected exactly one publish, got: " + publisher.events);
        return publisher.events.get(0);
    }

    private WebSocketSession session() {
        return mock(WebSocketSession.class);
    }

    private TextMessage message(String payload) {
        return new TextMessage(payload);
    }

    private String lightPayload() {
        return payloadFor(LIGHT_ENTITY_ID, "state_changed");
    }

    private String binarySensorPayload() {
        return payloadFor(SENSOR_ENTITY_ID, "state_changed");
    }

    private String payloadFor(String entityId, String eventType) {
        return """
            {
              "id": 1,
              "type": "event",
              "event": {
                "event_type": "%s",
                "data": {
                  "entity_id": "%s",
                  "old_state": {
                    "entity_id": "%s",
                    "state": "off",
                    "attributes": {"brightness": 10, "device_class": "occupancy"},
                    "last_changed": "2026-08-29T22:00:00.000+01:00",
                    "last_updated": "2026-08-29T22:00:00.000+01:00"
                  },
                  "new_state": {
                    "entity_id": "%s",
                    "state": "on",
                    "attributes": {"brightness": 200, "device_class": "occupancy"},
                    "last_changed": "2026-08-29T22:00:00.000+01:00",
                    "last_updated": "2026-08-29T22:00:00.000+01:00"
                  }
                },
                "origin": "LOCAL",
                "time_fired": "2026-08-29T22:00:00.000+01:00"
              }
            }
            """.formatted(eventType, entityId, entityId, entityId);
    }

    /** Records what the handler publishes; avoids the two-overload ambiguity of a lambda. */
    private static final class CapturingPublisher implements ApplicationEventPublisher {

        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(ApplicationEvent event) {
            events.add(event);
        }

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }
}
