package com.example.springboot.homeassistant.controller;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.models.LightEntityClassifier;
import com.example.springboot.homeassistant.services.BrightnessAutomationExclusionRegistry;
import com.example.springboot.homeassistant.services.DelayedActionService;
import com.example.springboot.homeassistant.services.LightBrightnessService;
import com.example.springboot.homeassistant.services.TimeOfDayBrightnessPolicy;
import com.example.springboot.homeassistant.services.TimeOfDayBrightnessScheduleService;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/homeassistant")
@RequiredArgsConstructor
@Slf4j
public class HomeAssistantController {

    private final LightBrightnessService lightBrightnessService;
    private final DelayedActionService delayedActionService;

    @GetMapping("/lights")
    public List<LightEntity> getLightEntities() {
        return lightBrightnessService.getLightEntities();
    }

    @GetMapping("/delayed-actions")
    public List<DelayedActionItem> getDelayedActions() {
        return delayedActionService.getPendingActions().stream()
            .map(action -> new DelayedActionItem(
                action.actionKey(),
                action.lightEntityId(),
                action.phase(),
                action.attempt(),
                action.rootScheduledAtEpochMs(),
                action.nextFireTime()
            ))
            .toList();
    }

    @GetMapping("/lights/{entityId:.+}")
    public LightEntity getLightEntity(@PathVariable String entityId) {
        return lightBrightnessService.getLightEntity(entityId);
    }

    @PostMapping("/lights/on")
    public void turnOnLight(
        @RequestParam("entity_id") String entityId,
        @RequestParam(value = "brightness", required = false) Integer brightness,
        @RequestParam(value = "rgb_color", required = false) List<Integer> rgbColor
    ) {
        log.info("Turning on light with entityId: {} brightness={} rgbColor={}", entityId, brightness, rgbColor);
        lightBrightnessService.turnOnLight(entityId, brightness, rgbColor);
    }

    @PostMapping("/lights/off")
    public void turnOffLight(@RequestParam("entity_id") String entityId) {
        log.info("Turning off light with entityId: {}", entityId);
        lightBrightnessService.turnOffLight(entityId);
    }

    @PostMapping("/lights/brightness")
    public void setBrightness(@RequestParam("entity_id") String entityId, @RequestParam("brightness") int brightness) {
        log.info("Setting brightness of light with entityId: {} to {}", entityId, brightness);
        lightBrightnessService.setBrightness(entityId, brightness);
    }

    @PostMapping("/lights/color")
    public void setColor(@RequestParam("entity_id") String entityId, @RequestParam("rgb_color") List<Integer> rgbColor) {
        log.info("Setting color of light with entityId: {} to {}", entityId, rgbColor);
        lightBrightnessService.setColor(entityId, rgbColor);
    }

    @PostMapping("/lights/manual-override/clear")
    public void clearManualOverride(@RequestParam("entity_id") String entityId) {
        lightBrightnessService.clearManualBrightnessOverride(entityId);
    }

    @GetMapping("/lights/brightness-dashboard")
    public BrightnessDashboardResponse getBrightnessDashboard() {
        LocalTime now = LocalTime.now();
        int min = TimeOfDayBrightnessPolicy.MIN_BRIGHTNESS;
        int max = TimeOfDayBrightnessPolicy.MAX_BRIGHTNESS;
        int currentTargetBrightness = TimeOfDayBrightnessScheduleService.brightnessAt(now, min, max);
        TimeOfDayBrightnessScheduleService.SchedulePosition schedulePosition = TimeOfDayBrightnessScheduleService.currentPosition(now);

        Map<String, Instant> activeOverrides = lightBrightnessService.getActiveManualBrightnessOverrides();

        List<LightDashboardItem> lights = lightBrightnessService.getLightEntities().stream()
            .map(light -> {
                String entityId = light.entityId();
                Instant overrideUntil = entityId == null ? null : activeOverrides.get(normalizeLightEntityId(entityId));
                return new LightDashboardItem(
                    light.attributes() != null ? light.attributes().friendlyName() : null,
                    entityId,
                    light.state(),
                    light.attributes() != null ? light.attributes().brightness() : null,
                    light.attributes() != null && light.attributes().colorMode() != null ? light.attributes().colorMode().name() : null,
                    LightEntityClassifier.classify(light).name(),
                    BrightnessAutomationExclusionRegistry.isExcluded(entityId),
                    overrideUntil
                );
            })
            .sorted(Comparator.comparing(item -> item.entityId() == null ? "" : item.entityId()))
            .toList();

        List<ManualOverrideLeaseItem> manualOverrideLeases = activeOverrides.entrySet().stream()
            .map(entry -> {
                String entityId = entry.getKey();
                String friendlyName = lights.stream()
                    .filter(light -> entityId.equals(normalizeLightEntityId(light.entityId())))
                    .map(LightDashboardItem::friendlyName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(null);
                return new ManualOverrideLeaseItem(friendlyName, entityId, entry.getValue());
            })
            .sorted(Comparator.comparing(ManualOverrideLeaseItem::entityId))
            .toList();

        CurrentScheduleState currentState = new CurrentScheduleState(
            now.toString(),
            now.toSecondOfDay(),
            currentTargetBrightness,
            schedulePosition.phase(),
            schedulePosition.progress()
        );

        return new BrightnessDashboardResponse(
            currentState,
            TimeOfDayBrightnessScheduleService.anchors(min, max),
            TimeOfDayBrightnessScheduleService.points(min, max, 15),
            lights,
            manualOverrideLeases,
            ZoneId.systemDefault().toString()
        );
    }

    private String normalizeLightEntityId(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return entityId;
        }
        return entityId.startsWith("light.") ? entityId : "light." + entityId;
    }

    public record BrightnessDashboardResponse(
        CurrentScheduleState current,
        List<TimeOfDayBrightnessScheduleService.ScheduleAnchor> scheduleAnchors,
        List<TimeOfDayBrightnessScheduleService.SchedulePoint> schedulePoints,
        List<LightDashboardItem> lights,
        List<ManualOverrideLeaseItem> manualOverrideLeases,
        String timezone
    ) {
    }

    public record CurrentScheduleState(String currentTime, int currentSecondOfDay, int targetBrightness, String phase, double phaseProgress) {
    }

    public record LightDashboardItem(
        String friendlyName,
        String entityId,
        String state,
        Integer brightness,
        String colorMode,
        String lightKind,
        boolean excluded,
        Instant manualOverrideUntil
    ) {
    }

    public record ManualOverrideLeaseItem(String friendlyName, String entityId, Instant overrideUntil) {
    }

    public record DelayedActionItem(
        String actionKey,
        String lightEntityId,
        String phase,
        int attempt,
        long rootScheduledAtEpochMs,
        Instant nextFireTime
    ) {
    }
}
