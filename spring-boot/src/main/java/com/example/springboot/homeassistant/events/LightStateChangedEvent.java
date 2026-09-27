package com.example.springboot.homeassistant.events;

import com.example.springboot.homeassistant.models.LightEntity;

/** A {@code state_changed} event for a {@code light.*} entity. */
public final class LightStateChangedEvent extends StateChangedEvent {

    public static final String DOMAIN = "light";

    private final LightEntity oldState;
    private final LightEntity newState;

    public LightStateChangedEvent(String entityId, LightEntity oldState, LightEntity newState) {
        super(entityId, DOMAIN);
        this.oldState = oldState;
        this.newState = newState;
    }

    /** The previous state, or {@code null} when Home Assistant did not send one. */
    public LightEntity oldState() {
        return oldState;
    }

    /** The new state, or {@code null} when the payload did not carry one. */
    public LightEntity newState() {
        return newState;
    }
}
