package com.example.springboot.homeassistant.jobs;

import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.models.LightEntityClassifier;
import com.example.springboot.homeassistant.models.LightEntityClassifier.LightKind;
import com.example.springboot.homeassistant.services.BrightnessAutomationExclusionRegistry;
import com.example.springboot.homeassistant.services.LightBrightnessService;
import com.example.springboot.homeassistant.services.TimeOfDayBrightnessPolicy;
import com.example.springboot.homeassistant.services.TimeOfDayBrightnessScheduleService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeOfDayBrightnessJob implements Job {

    private static final int BRIGHTNESS_TOLERANCE = 2;

    @Autowired
    private LightBrightnessService lightBrightnessService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            int targetBrightness = TimeOfDayBrightnessScheduleService.setBrightnessForTimeOfDay(
                TimeOfDayBrightnessPolicy.MIN_BRIGHTNESS,
                TimeOfDayBrightnessPolicy.MAX_BRIGHTNESS
            );
            List<LightEntity> onLights = lightBrightnessService.getLightEntities().stream()
                .filter(light -> "on".equalsIgnoreCase(light.state()))
                .toList();

            int updatedCount = 0;
            int skippedOverrideCount = 0;
            int skippedExcludedCount = 0;
            int skippedUnsupportedCount = 0;
            for (LightEntity light : onLights) {
                String entityId = light.entityId();
                if (entityId == null) {
                    continue;
                }

                if (BrightnessAutomationExclusionRegistry.isExcluded(entityId)) {
                    skippedExcludedCount++;
                    continue;
                }

                if (LightEntityClassifier.classify(light) != LightKind.DIMMABLE_OR_COLOR) {
                    skippedUnsupportedCount++;
                    continue;
                }

                if (lightBrightnessService.hasActiveManualBrightnessOverride(entityId)) {
                    skippedOverrideCount++;
                    continue;
                }

                Integer currentBrightness = light.attributes() != null ? light.attributes().brightness() : null;
                if (currentBrightness != null
                    && Math.abs(currentBrightness - targetBrightness) <= BRIGHTNESS_TOLERANCE) {
                    continue;
                }

                lightBrightnessService.setBrightnessFromTimeOfDayAutomation(entityId, targetBrightness);
                updatedCount++;
            }

            log.debug(
                "Time-of-day brightness run complete: target={} onLights={} updated={} skippedByOverride={} skippedByExclusion={} skippedByLightKind={}",
                targetBrightness,
                onLights.size(),
                updatedCount,
                skippedOverrideCount,
                skippedExcludedCount,
                skippedUnsupportedCount
            );
        } catch (RuntimeException e) {
            throw new JobExecutionException("Failed to apply time-of-day brightness", e, false);
        }
    }
}
