package com.example.springboot.homeassistant.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericApplicationContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.example.springboot.homeassistant.models.BinarySensorEntity;
import com.example.springboot.homeassistant.models.BinarySensorEntity.Attributes;
import com.example.springboot.homeassistant.models.LightEntity;

class StateChangedEventRouterTest {

    private static final String LIGHT_ENTITY_ID = "light.bathroom_1";
    private static final String SENSOR_ENTITY_ID = "binary_sensor.bathroom_motion_sensor_occupancy";

    private Logger routerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogAppender() {
        routerLogger = (Logger) LoggerFactory.getLogger(StateChangedEventRouter.class);
        appender = new ListAppender<>();
        appender.start();
        routerLogger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        routerLogger.detachAppender(appender);
    }

    @Test
    void deliversEventToSubscriberRegisteredForThatEventType() {
        List<String> handled = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> true, e -> handled.add(e.entityId()), 10, "light"));

        router.onApplicationEvent(lightEvent());

        assertEquals(List.of(LIGHT_ENTITY_ID), handled);
    }

    @Test
    void doesNotDeliverToSubscriberRegisteredForASiblingEventType() {
        List<String> handled = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(BinarySensorStateChangedEvent.class, e -> true, e -> handled.add(e.entityId()), 10, "binary"));

        router.onApplicationEvent(lightEvent());

        assertTrue(handled.isEmpty());
    }

    @Test
    void runsSubscriberWhoseCriteriaAccept() {
        List<String> handled = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> LIGHT_ENTITY_ID.equals(e.entityId()),
                e -> handled.add("matched"), 10, "bathroom-only"));

        router.onApplicationEvent(lightEvent());

        assertEquals(List.of("matched"), handled);
    }

    @Test
    void skipsSubscriberWhoseCriteriaReject() {
        List<String> handled = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> "light.kitchen".equals(e.entityId()),
                e -> handled.add("matched"), 10, "kitchen-only"));

        router.onApplicationEvent(lightEvent());

        assertTrue(handled.isEmpty());
    }

    @Test
    void runsSubscribersInOrderRegardlessOfDeclarationOrder() {
        List<String> calls = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> true, e -> calls.add("third"), 30, "third")
            .subscribe(LightStateChangedEvent.class, e -> true, e -> calls.add("first"), 10, "first")
            .subscribe(LightStateChangedEvent.class, e -> true, e -> calls.add("second"), 20, "second"));

        router.onApplicationEvent(lightEvent());

        assertEquals(List.of("first", "second", "third"), calls);
    }

    @Test
    void breaksOrderTiesByName() {
        List<String> calls = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> true, e -> calls.add("zulu"), 10, "zulu")
            .subscribe(LightStateChangedEvent.class, e -> true, e -> calls.add("alpha"), 10, "alpha"));

        router.onApplicationEvent(lightEvent());

        assertEquals(List.of("alpha", "zulu"), calls);
    }

    @Test
    void throwingSubscriberDoesNotPreventLaterSubscriberFromRunning() {
        List<String> calls = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> true, e -> {
                throw new IllegalStateException("boom");
            }, 10, "exploding")
            .subscribe(LightStateChangedEvent.class, e -> true, e -> calls.add("survivor"), 20, "survivor"));

        router.onApplicationEvent(lightEvent());

        assertEquals(List.of("survivor"), calls);
    }

    @Test
    void throwingSubscriberIsLoggedWithItsSubscriptionName() {
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> true, e -> {
                throw new IllegalStateException("boom");
            }, 10, "exploding"));

        router.onApplicationEvent(lightEvent());

        assertTrue(
            appender.list.stream().anyMatch(logged ->
                logged.getLevel() == Level.WARN
                    && logged.getFormattedMessage().contains("exploding")
                    && logged.getFormattedMessage().contains(LIGHT_ENTITY_ID)),
            "expected a WARN naming the failing subscription and the entity, got: " + appender.list
        );
    }

    @Test
    void throwingCriteriaDoesNotAbortDispatch() {
        List<String> calls = new ArrayList<>();
        StateChangedEventRouter router = routerWith(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, e -> {
                throw new IllegalStateException("bad predicate");
            }, e -> calls.add("unreachable"), 10, "bad-criteria")
            .subscribe(LightStateChangedEvent.class, e -> true, e -> calls.add("survivor"), 20, "survivor"));

        router.onApplicationEvent(lightEvent());

        assertEquals(List.of("survivor"), calls);
    }

    /**
     * The router is registered as {@code ApplicationListener<StateChangedEvent>} but must receive
     * the concrete subclasses. Spring's multicaster resolves the event type from the runtime class
     * and checks assignability, so this holds without any work in the router — but if it ever
     * stopped holding, every subscription would silently go dead. Exercised against a real
     * {@link GenericApplicationContext} because the behaviour belongs to Spring, not to the router.
     */
    @Test
    void springDeliversSubclassEventsToAListenerDeclaredForTheBaseType() {
        List<String> received = new ArrayList<>();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(StateChangedEventRouter.class, () -> routerWith(
                new StateChangedSubscriptionRegistry()
                    .subscribe(LightStateChangedEvent.class, e -> true, e -> received.add(e.entityId()), 10, "light")));
            context.refresh();

            context.publishEvent(lightEvent());
        }

        assertEquals(List.of(LIGHT_ENTITY_ID), received);
    }

    @Test
    void deviceClassIsReadFromTheNewState() {
        BinarySensorStateChangedEvent event = new BinarySensorStateChangedEvent(
            SENSOR_ENTITY_ID,
            sensorState("off", "occupancy"),
            sensorState("on", "occupancy")
        );

        assertEquals("occupancy", event.deviceClass());
        assertEquals(BinarySensorStateChangedEvent.DOMAIN, event.domain());
    }

    @Test
    void deviceClassIsNullWhenAttributesOrStateAreMissing() {
        BinarySensorStateChangedEvent noAttributes = new BinarySensorStateChangedEvent(
            SENSOR_ENTITY_ID, null, new BinarySensorEntity(SENSOR_ENTITY_ID, "on", null, null, null));
        BinarySensorStateChangedEvent noNewState = new BinarySensorStateChangedEvent(
            SENSOR_ENTITY_ID, null, null);

        assertNull(noAttributes.deviceClass());
        assertNull(noNewState.deviceClass());
    }

    private StateChangedEventRouter routerWith(StateChangedSubscriptionRegistry registry) {
        return new StateChangedEventRouter(registry.build());
    }

    private LightStateChangedEvent lightEvent() {
        return new LightStateChangedEvent(LIGHT_ENTITY_ID, lightState("off"), lightState("on"));
    }

    private LightEntity lightState(String state) {
        return new LightEntity(LIGHT_ENTITY_ID, state, null, null, null, null);
    }

    private BinarySensorEntity sensorState(String state, String deviceClass) {
        return new BinarySensorEntity(
            SENSOR_ENTITY_ID,
            state,
            new Attributes("Bathroom Motion", deviceClass),
            null,
            null
        );
    }
}
