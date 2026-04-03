package com.example.springboot.services;

import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.springboot.clients.HomeAssistantHttpClient;
import com.example.springboot.models.home_assistant.LightEntity;
import com.example.springboot.websockets.messages.HaWsStateChangedEvent;

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
    private static final LocalTime SEVEN_AM = LocalTime.of(7, 0);
    private static final LocalTime EIGHT_AM = LocalTime.of(8, 0);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);
    private static final double MORNING_STEEPNESS = 5.0;
    private static final double EVENING_STEEPNESS = 8.0;

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

    public void setColor(String entityId, List<Integer> rgbColor) {
        if (rgbColor == null || rgbColor.size() != 3) {
            throw new IllegalArgumentException("rgbColor must contain exactly 3 values");
        }
        turnOnLight(entityId, null, rgbColor);
    }

    @Async
    public void handleLightStateChanged(HaWsStateChangedEvent<LightEntity> event) {
        try {
            String prettyEvent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
            log.info("Handling light state changed event:\n{}", prettyEvent);
        } catch (RuntimeException e) {
            log.warn("Handling light state changed event (failed to pretty serialize): {}", event, e);
        }



        // Implement logic to adjust brightness based on the new state of the light
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

    private int setBrightnessForTimeOfDay(int minBrightness, int maxBrightness) {
        LocalTime now = LocalTime.now();
        int low = minBrightness;
        int high = maxBrightness;

        if (now.isBefore(SEVEN_AM)) {
            return low;
        }

        if (now.isBefore(EIGHT_AM)) {
            int startSecond = SEVEN_AM.toSecondOfDay();
            int endSecond = EIGHT_AM.toSecondOfDay();
            int nowSecond = now.toSecondOfDay();

            double progress = (double) (nowSecond - startSecond) / (endSecond - startSecond);
            double sigmoidAtStart = 1.0 / (1.0 + Math.exp(-MORNING_STEEPNESS * (0.0 - 0.5)));
            double sigmoidAtEnd = 1.0 / (1.0 + Math.exp(-MORNING_STEEPNESS * (1.0 - 0.5)));
            double sigmoidNow = 1.0 / (1.0 + Math.exp(-MORNING_STEEPNESS * (progress - 0.5)));

            double normalized = (sigmoidNow - sigmoidAtStart) / (sigmoidAtEnd - sigmoidAtStart);
            double brightness = low + (high - low) * normalized;
            return (int) Math.round(brightness);
        }

        if (now.isBefore(SIX_PM)) {
            return high;
        }

        if (!now.isBefore(NINE_PM)) {
            return low;
        }

        int startSecond = SIX_PM.toSecondOfDay();
        int endSecond = NINE_PM.toSecondOfDay();
        int nowSecond = now.toSecondOfDay();

        double progress = (double) (nowSecond - startSecond) / (endSecond - startSecond);

        double sigmoidAtStart = 1.0 / (1.0 + Math.exp(-EVENING_STEEPNESS * (0.0 - 0.5)));
        double sigmoidAtEnd = 1.0 / (1.0 + Math.exp(-EVENING_STEEPNESS * (1.0 - 0.5)));
        double sigmoidNow = 1.0 / (1.0 + Math.exp(-EVENING_STEEPNESS * (progress - 0.5)));

        double normalized = (sigmoidNow - sigmoidAtStart) / (sigmoidAtEnd - sigmoidAtStart);
        double brightness = high - (high - low) * normalized;

        return (int) Math.round(brightness);
    }
}