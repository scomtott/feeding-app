package com.example.springboot.homeassistant.events;

import com.example.springboot.homeassistant.models.BinarySensorEntity;

/**
 * A {@code state_changed} event for a {@code binary_sensor.*} entity.
 *
 * <p>In Home Assistant {@code binary_sensor} and {@code sensor} are sibling platforms that each
 * derive directly from {@code Entity} — neither inherits the other — and their device classes come
 * from separate enums with disjoint members. That is why this is a sibling of
 * {@link LightStateChangedEvent} rather than a subclass of a shared sensor event, and why
 * {@link #deviceClass()} lives here instead of on {@link StateChangedEvent}.
 */
public final class BinarySensorStateChangedEvent extends StateChangedEvent {

    public static final String DOMAIN = "binary_sensor";

    private final BinarySensorEntity oldState;
    private final BinarySensorEntity newState;

    public BinarySensorStateChangedEvent(String entityId, BinarySensorEntity oldState, BinarySensorEntity newState) {
        super(entityId, DOMAIN);
        this.oldState = oldState;
        this.newState = newState;
    }

    /** The previous state, or {@code null} when Home Assistant did not send one. */
    public BinarySensorEntity oldState() {
        return oldState;
    }

    /** The new state, or {@code null} when the payload did not carry one. */
    public BinarySensorEntity newState() {
        return newState;
    }

    /** The device class reported for the new state, or {@code null} when absent. */
    public String deviceClass() {
        return newState != null && newState.attributes() != null
            ? newState.attributes().deviceClass()
            : null;
    }
}
