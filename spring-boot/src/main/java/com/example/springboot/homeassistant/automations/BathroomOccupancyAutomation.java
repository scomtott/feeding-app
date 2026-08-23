package com.example.springboot.homeassistant.automations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.springboot.homeassistant.automations.events.OccupancyStateChangedEvent;
import com.example.springboot.homeassistant.client.HomeAssistantHttpClient;
import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.properties.BathroomOccupancyAutomationProperties;
import com.example.springboot.homeassistant.services.DelayedActionService;
import com.example.springboot.homeassistant.services.LightBrightnessService;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BathroomOccupancyAutomation {

    private static final LocalTime EIGHT_AM = LocalTime.of(8, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);
    private static final List<Integer> RED_RGB_COLOR = List.of(255, 0, 0);
    private static final Duration STARTUP_OFF_DELAY = Duration.ofMinutes(5);
    private static final Duration MANUAL_LEASE_DURATION = Duration.ofHours(1);
    private static final Duration MOTION_TURN_ON_ACK_WINDOW = Duration.ofSeconds(30);

    private String turnOffActionKey() {
        return "automation:bathroom-occupancy:" + properties.getSensorEntityId() + ":" + properties.getLightEntityId() + ":turn_off";
    }

    private final BathroomOccupancyAutomationProperties properties;
    private final DelayedActionService delayedActionService;
    private final LightBrightnessService lightBrightnessService;
    private final HomeAssistantHttpClient homeAssistantHttpClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Instant> manualLeaseUntil = new AtomicReference<>();
    private final AtomicReference<Instant> pendingMotionTurnOnAckUntil = new AtomicReference<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!properties.isEnabled()) {
            return;
        }

        String configuredSensor = properties.getSensorEntityId();
        String configuredLight = properties.getLightEntityId();
        if (configuredSensor == null || configuredSensor.isBlank() || configuredLight == null || configuredLight.isBlank()) {
            return;
        }

        String actionKey = turnOffActionKey();
        if (delayedActionService.isScheduled(actionKey)) {
            return;
        }

        try {
            String response = homeAssistantHttpClient.get("/api/states/" + normalizeLightEntityId(configuredLight));
            LightEntity lightEntity = objectMapper.readValue(response, LightEntity.class);
            if (lightEntity != null && "on".equalsIgnoreCase(lightEntity.state())) {
                delayedActionService.scheduleTurnOffLight(actionKey, configuredLight, STARTUP_OFF_DELAY);
                log.info("Startup recovery scheduled turn-off for {} in {} seconds.", configuredLight, STARTUP_OFF_DELAY.toSeconds());
            }
        } catch (RuntimeException e) {
            log.warn("Failed startup recovery check for light {}", configuredLight, e);
        }
    }

    @EventListener
    public void onOccupancyChanged(OccupancyStateChangedEvent event) {
        log.info("Received occupancy state changed event.");
        if (!properties.isEnabled()) {
            log.info("Bathroom occupancy automation is disabled. Ignoring event.");
            return;
        }

        String configuredSensor = properties.getSensorEntityId();
        String configuredLight = properties.getLightEntityId();
        if (configuredSensor == null || configuredSensor.isBlank() || configuredLight == null || configuredLight.isBlank()) {
            log.info("Bathroom occupancy automation is not properly configured. Ignoring event.");
            return;
        }

        if (!configuredSensor.equals(event.entityId())) {
            log.info("Received occupancy state changed event for sensor {}, but configured sensor is {}. Ignoring event.", event.entityId(), configuredSensor);
            return;
        }

        String actionKey = turnOffActionKey();

        if (hasActiveManualLease()) {
            delayedActionService.cancel(actionKey);
            log.info("Ignoring occupancy detected event while manual lease is active for {}.", configuredLight);
            return;
        }

        if (event.occupied()) {
            delayedActionService.cancel(actionKey);
            pendingMotionTurnOnAckUntil.set(Instant.now().plus(MOTION_TURN_ON_ACK_WINDOW));
            if (isWithinNormalLightingHours()) {
                lightBrightnessService.turnOnLightWhite(configuredLight, 3);
            } else {
                lightBrightnessService.turnOnLight(configuredLight, 3, RED_RGB_COLOR);
            }
            log.info("Bathroom occupancy detected for {}. Turned on {} and cancelled pending off action.", configuredSensor, configuredLight);
            delayedActionService.scheduleTurnOffLight(actionKey, configuredLight, properties.getOffDelay());
            return;
        }

        log.info("Bathroom occupancy clear for {}.", configuredSensor);
    }

    public void handleBathroomLightStateChanged(HaWsStateChangedEvent<LightEntity> event) {
        if (!properties.isEnabled() || event == null || event.event() == null || event.event().data() == null) {
            return;
        }

        String configuredLight = properties.getLightEntityId();
        if (configuredLight == null || configuredLight.isBlank()) {
            return;
        }

        LightEntity oldState = event.event().data().oldState();
        LightEntity newState = event.event().data().newState();
        if (newState == null || newState.entityId() == null) {
            return;
        }
        if (oldState == null) {
            return;
        }

        String normalizedConfiguredLight = normalizeLightEntityId(configuredLight);
        if (!normalizedConfiguredLight.equalsIgnoreCase(normalizeLightEntityId(newState.entityId()))) {
            return;
        }

        boolean oldOn = oldState != null && "on".equalsIgnoreCase(oldState.state());
        boolean newOn = "on".equalsIgnoreCase(newState.state());
        boolean contextChanged = !Objects.equals(contextId(oldState), contextId(newState));

        if (!newOn) {
            pendingMotionTurnOnAckUntil.set(null);
            if (hasActiveManualLease()) {
                clearManualLeaseInternal();
            }
            return;
        }

        if (consumePendingMotionTurnOnAck()) {
            return;
        }

        if (oldOn) {
            if (contextChanged && delayedActionService.isScheduled(turnOffActionKey())) {
                activateManualLease(normalizedConfiguredLight);
            }
            return;
        }

        activateManualLease(normalizedConfiguredLight);
    }

    public BathroomManualLease getActiveManualLease() {
        String configuredLight = properties.getLightEntityId();
        if (configuredLight == null || configuredLight.isBlank() || !hasActiveManualLease()) {
            return null;
        }

        Instant leaseUntil = manualLeaseUntil.get();
        if (leaseUntil == null || leaseUntil.isBefore(Instant.now())) {
            return null;
        }

        return new BathroomManualLease(normalizeLightEntityId(configuredLight), leaseUntil);
    }

    public boolean clearManualLease(String entityId) {
        String configuredLight = properties.getLightEntityId();
        if (configuredLight == null || configuredLight.isBlank() || entityId == null || entityId.isBlank()) {
            return false;
        }

        if (!normalizeLightEntityId(configuredLight).equalsIgnoreCase(normalizeLightEntityId(entityId))) {
            return false;
        }

        pendingMotionTurnOnAckUntil.set(null);
        return clearManualLeaseInternal();
    }

    private void activateManualLease(String normalizedLightEntityId) {
        Instant leaseUntil = Instant.now().plus(MANUAL_LEASE_DURATION);
        manualLeaseUntil.set(leaseUntil);
        delayedActionService.cancel(turnOffActionKey());
        log.info("Activated bathroom manual lease for {} until {}", normalizedLightEntityId, leaseUntil);
    }

    private boolean clearManualLeaseInternal() {
        Instant previousLeaseUntil = manualLeaseUntil.getAndSet(null);
        if (previousLeaseUntil != null) {
            log.info("Cleared bathroom manual lease after manual turn-off.");
            return true;
        }
        return false;
    }

    private boolean hasActiveManualLease() {
        Instant leaseUntil = manualLeaseUntil.get();
        if (leaseUntil == null) {
            return false;
        }

        if (leaseUntil.isAfter(Instant.now())) {
            return true;
        }

        manualLeaseUntil.compareAndSet(leaseUntil, null);
        return false;
    }

    private boolean consumePendingMotionTurnOnAck() {
        Instant expiresAt = pendingMotionTurnOnAckUntil.get();
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt.isBefore(Instant.now())) {
            pendingMotionTurnOnAckUntil.compareAndSet(expiresAt, null);
            return false;
        }

        return pendingMotionTurnOnAckUntil.compareAndSet(expiresAt, null);
    }

    private String contextId(LightEntity lightEntity) {
        return lightEntity != null && lightEntity.context() != null ? lightEntity.context().id() : null;
    }

    private String normalizeLightEntityId(String entityId) {
        return entityId.startsWith("light.") ? entityId : "light." + entityId;
    }

    private boolean isWithinNormalLightingHours() {
        LocalTime now = LocalTime.now();
        return !now.isBefore(EIGHT_AM) && now.isBefore(NINE_PM);
    }

    public record BathroomManualLease(String entityId, Instant leaseUntil) {
    }
}
