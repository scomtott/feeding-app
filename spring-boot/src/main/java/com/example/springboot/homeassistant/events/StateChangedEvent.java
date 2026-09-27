package com.example.springboot.homeassistant.events;

import org.springframework.context.ApplicationEvent;

/**
 * Base type for the events published for Home Assistant {@code state_changed} payloads.
 *
 * <p>Deliberately <strong>non-generic</strong>. {@code ApplicationListener<E extends ApplicationEvent>}
 * is a hard compile-time bound, so this must extend {@link ApplicationEvent}; keeping the type
 * parameter off the base class lets the router match listeners with a plain
 * {@link Class#isAssignableFrom} check instead of resolving a generic supertype.
 *
 * <p>Subclasses are siblings rather than a hierarchy — see
 * {@link LightStateChangedEvent} and {@link BinarySensorStateChangedEvent}.
 */
public abstract class StateChangedEvent extends ApplicationEvent {

    private final String entityId;
    private final String domain;

    /**
     * Uses the entity id as the {@link ApplicationEvent} source, rather than the publishing bean.
     * That keeps {@code getSource()} meaningful (the entity whose state changed) and keeps the
     * event serializable for the services' debug logging — a bean source would drag its whole
     * object graph into the log.
     */
    protected StateChangedEvent(String entityId, String domain) {
        super(entityId == null ? "unknown" : entityId);
        this.entityId = entityId;
        this.domain = domain;
    }

    /** The Home Assistant entity id, or {@code "unknown"} when the payload did not carry one. */
    public String entityId() {
        return entityId;
    }

    /** The entity domain parsed from {@link #entityId()}, e.g. {@code light}. */
    public String domain() {
        return domain;
    }
}
