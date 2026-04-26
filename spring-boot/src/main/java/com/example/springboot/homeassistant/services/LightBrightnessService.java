package com.example.springboot.homeassistant.services;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.springboot.homeassistant.client.HomeAssistantHttpClient;
import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.models.LightEntityClassifier;
import com.example.springboot.homeassistant.models.LightEntityClassifier.LightKind;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class LightBrightnessService {

    private final HomeAssistantHttpClient homeAssistantHttpClient;
    private final ObjectMapper objectMapper;
    private final BathroomTelemetryStorageService bathroomTelemetryStorageService;
    private final IlluminanceSensorService illuminanceSensorService;
    private static final Duration MANUAL_OVERRIDE_DURATION = Duration.ofMinutes(60);
    private static final Duration AUTOMATION_ACK_WINDOW = Duration.ofSeconds(30);
    private static final int BRIGHTNESS_TOLERANCE = 2;

    private final Map<String, PendingAutomationBrightnessUpdate> pendingAutomationBrightnessUpdates = new ConcurrentHashMap<>();
    private final Map<String, Instant> manualBrightnessOverrideUntil = new ConcurrentHashMap<>();

    public List<LightEntity> getLightEntities() {
        String response = homeAssistantHttpClient.get("/api/states");
        try {
            List<LightEntity> entities = objectMapper.readValue(response, new TypeReference<List<LightEntity>>() {
            });
            return entities.stream()
                .filter(entity -> entity.entityId() != null && entity.entityId().startsWith("light."))
                .toList();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to deserialize Home Assistant light entities", e);
        }
    }

    public LightEntity getLightEntity(String entityId) {
        String normalizedEntityId = normalizeLightEntityId(entityId);
        String response = homeAssistantHttpClient.get("/api/states/" + normalizedEntityId);
        try {
            return objectMapper.readValue(response, LightEntity.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to deserialize Home Assistant light entity: " + normalizedEntityId, e);
        }
    }

    public void turnOnLight(String entityId) {
        postLightServiceAction("turn_on", lightServicePayload(entityId));
    }

    public void turnOnLight(String entityId, Integer brightness) {
        postLightServiceAction(
            "turn_on",
            lightServicePayload(entityId)
                .withBrightness(brightness)
        );
    }

    public void turnOnLight(String entityId, Integer brightness, List<Integer> rgbColor) {
        if (rgbColor == null || rgbColor.size() != 3) {
            throw new IllegalArgumentException("rgbColor must contain exactly 3 values");
        }
        postLightServiceAction(
            "turn_on",
            lightServicePayload(entityId)
                .withBrightness(brightness)
                .withRgbColor(rgbColor.get(0), rgbColor.get(1), rgbColor.get(2))
        );
    }

    public void turnOnLightWhite(String entityId, Integer brightness) {
        postLightServiceAction(
            "turn_on",
            lightServicePayload(entityId)
                .withWhite(brightness)
        );
    }

    public void turnOffLight(String entityId) {
        postLightServiceAction("turn_off", lightServicePayload(entityId));
    }

    public void setBrightness(String entityId, int brightness) {
        turnOnLight(entityId, brightness);
    }

    public void setBrightnessFromTimeOfDayAutomation(String entityId, int brightness) {
        String normalizedEntityId = normalizeLightEntityId(entityId);
        int clampedBrightness = clampBrightness(brightness);
        pendingAutomationBrightnessUpdates.put(
            normalizedEntityId,
            new PendingAutomationBrightnessUpdate(clampedBrightness, Instant.now().plus(AUTOMATION_ACK_WINDOW))
        );
        setBrightness(normalizedEntityId, clampedBrightness);
    }

    public boolean hasActiveManualBrightnessOverride(String entityId) {
        return getManualBrightnessOverrideUntil(entityId) != null;
    }

    public Instant getManualBrightnessOverrideUntil(String entityId) {
        String normalizedEntityId = normalizeLightEntityId(entityId);
        Instant overrideUntil = manualBrightnessOverrideUntil.get(normalizedEntityId);
        if (overrideUntil == null) {
            return null;
        }

        if (overrideUntil.isAfter(Instant.now())) {
            return overrideUntil;
        }

        manualBrightnessOverrideUntil.remove(normalizedEntityId, overrideUntil);
        return null;
    }

    public Map<String, Instant> getActiveManualBrightnessOverrides() {
        Map<String, Instant> activeOverrides = new HashMap<>();
        for (String entityId : manualBrightnessOverrideUntil.keySet()) {
            Instant overrideUntil = getManualBrightnessOverrideUntil(entityId);
            if (overrideUntil != null) {
                activeOverrides.put(entityId, overrideUntil);
            }
        }
        return activeOverrides;
    }

    public boolean clearManualBrightnessOverride(String entityId) {
        String normalizedEntityId = normalizeLightEntityId(entityId);
        pendingAutomationBrightnessUpdates.remove(normalizedEntityId);
        return manualBrightnessOverrideUntil.remove(normalizedEntityId) != null;
    }

    public void setColor(String entityId, List<Integer> rgbColor) {
        if (rgbColor == null || rgbColor.size() != 3) {
            throw new IllegalArgumentException("rgbColor must contain exactly 3 values");
        }
        turnOnLight(entityId, null, rgbColor);
    }

    private void postLightServiceAction(String action, LightServicePayload payload) {
        try {
            String serializedPayload = objectMapper.writeValueAsString(payload);
            homeAssistantHttpClient.post("/api/services/light/" + action, serializedPayload);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to serialize Home Assistant light request payload", e);
        }
    }

    private String normalizeLightEntityId(String entityId) {
        return entityId.startsWith("light.") ? entityId : "light." + entityId;
    }

    private List<Integer> normalizeRgbColor(List<Integer> rgbColor) {
        if (rgbColor.size() != 3) {
            throw new IllegalArgumentException("rgbColor must contain exactly 3 values");
        }

        return rgbColor.stream()
            .map(value -> Math.max(0, Math.min(255, value)))
            .toList();
    }

    private int clampBrightness(int brightness) {
        return Math.max(0, Math.min(255, brightness));
    }

    private LightServicePayload lightServicePayload(String entityId) {
        return new LightServicePayload(normalizeLightEntityId(entityId));
    }

    @JsonInclude(Include.NON_NULL)
    private static final class LightServicePayload {
        private final String entityId;
        private Integer brightness;
        private int[] rgbColor;
        private Integer white;

        private LightServicePayload(String entityId) {
            this.entityId = entityId;
        }

        private LightServicePayload withBrightness(Integer brightness) {
            if (brightness != null) {
                this.brightness = Math.max(0, Math.min(255, brightness));
            }
            return this;
        }

        private LightServicePayload withRgbColor(int red, int green, int blue) {
            this.rgbColor = new int[] {red, green, blue};
            return this;
        }

        private LightServicePayload withWhite(Integer white) {
            if (white != null) {
                this.white = Math.max(0, Math.min(255, white));
            }
            return this;
        }

        @JsonProperty("entity_id")
        public String getEntityId() { return entityId; }

        @JsonProperty("brightness")
        public Integer getBrightness() { return brightness; }

        @JsonProperty("rgb_color")
        public int[] getRgbColor() { return rgbColor; }

        @JsonProperty("white")
        public Integer getWhite() { return white; }
    }

    @Async("telemetryExecutor")
    public void handleLightStateChanged(HaWsStateChangedEvent<LightEntity> event) {
        if (log.isDebugEnabled()) {
            try {
                String prettyEvent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
                log.debug("Handling light state changed event:\n{}", prettyEvent);
            } catch (RuntimeException e) {
                log.debug("Handling light state changed event (failed to pretty serialize)", e);
            }
        }

        if (event.event() == null || event.event().data() == null) {
            return;
        }

        LightEntity oldState = event.event().data().oldState();
        LightEntity newState = event.event().data().newState();
        if (newState == null) {
            return;
        }

        String entityId = newState.entityId();
        if (entityId == null) {
            return;
        }

        Integer oldBrightness = oldState != null && oldState.attributes() != null ? oldState.attributes().brightness() : null;
        Integer newBrightness = newState.attributes() != null ? newState.attributes().brightness() : null;

        boolean oldOn = oldState != null && "on".equalsIgnoreCase(oldState.state());
        boolean newOn = "on".equalsIgnoreCase(newState.state());

        if (!oldOn && newOn) {
            applyTimeOfDayBrightnessOnTurnOn(newState);
        }

        if (oldOn && newOn && newBrightness != null && !Objects.equals(oldBrightness, newBrightness)) {
            trackManualBrightnessOverride(entityId, newBrightness);
        }

        if (oldState == null || !bathroomTelemetryStorageService.isTrackedBathroomLight(entityId)) {
            return;
        }

        if (oldOn == newOn) {
            return;
        }

        Instant eventTs = HomeAssistantEventUtils.parseEventTimestamp(newState.lastUpdated(), newState.lastChanged());
        String payloadJson = HomeAssistantEventUtils.serializeEventSilently(objectMapper, event);

        bathroomTelemetryStorageService.storeLightStateEvent(entityId, newOn, eventTs, "ha-websocket", payloadJson);
        illuminanceSensorService.notifyContextStateChange(eventTs);
    }

    private void applyTimeOfDayBrightnessOnTurnOn(LightEntity lightEntity) {
        String entityId = lightEntity.entityId();
        if (entityId == null) {
            return;
        }

        String normalizedEntityId = normalizeLightEntityId(entityId);
        if (BrightnessAutomationExclusionRegistry.isExcluded(normalizedEntityId)) {
            return;
        }

        if (LightEntityClassifier.classify(lightEntity) != LightKind.DIMMABLE_OR_COLOR) {
            return;
        }

        if (hasActiveManualBrightnessOverride(normalizedEntityId)) {
            return;
        }

        int targetBrightness = TimeOfDayBrightnessScheduleService.setBrightnessForTimeOfDay(
            TimeOfDayBrightnessPolicy.MIN_BRIGHTNESS,
            TimeOfDayBrightnessPolicy.MAX_BRIGHTNESS
        );
        Integer currentBrightness = lightEntity.attributes() != null ? lightEntity.attributes().brightness() : null;
        if (currentBrightness != null && Math.abs(currentBrightness - targetBrightness) <= BRIGHTNESS_TOLERANCE) {
            return;
        }

        setBrightnessFromTimeOfDayAutomation(normalizedEntityId, targetBrightness);
    }

    private void trackManualBrightnessOverride(String entityId, int observedBrightness) {
        String normalizedEntityId = normalizeLightEntityId(entityId);

        if (BrightnessAutomationExclusionRegistry.isExcluded(normalizedEntityId)) {
            pendingAutomationBrightnessUpdates.remove(normalizedEntityId);
            manualBrightnessOverrideUntil.remove(normalizedEntityId);
            return;
        }

        int clampedObservedBrightness = clampBrightness(observedBrightness);
        Instant now = Instant.now();

        PendingAutomationBrightnessUpdate pendingUpdate = pendingAutomationBrightnessUpdates.get(normalizedEntityId);
        if (pendingUpdate != null) {
            if (!pendingUpdate.expiresAt().isBefore(now)
                && Math.abs(pendingUpdate.brightness() - clampedObservedBrightness) <= BRIGHTNESS_TOLERANCE) {
                pendingAutomationBrightnessUpdates.remove(normalizedEntityId, pendingUpdate);
                return;
            }

            if (pendingUpdate.expiresAt().isBefore(now)) {
                pendingAutomationBrightnessUpdates.remove(normalizedEntityId, pendingUpdate);
            }
        }

        Instant overrideUntil = now.plus(MANUAL_OVERRIDE_DURATION);
        manualBrightnessOverrideUntil.put(normalizedEntityId, overrideUntil);
        log.info("Registered manual brightness override for {} until {}", normalizedEntityId, overrideUntil);
    }

    private record PendingAutomationBrightnessUpdate(int brightness, Instant expiresAt) {
    }
}