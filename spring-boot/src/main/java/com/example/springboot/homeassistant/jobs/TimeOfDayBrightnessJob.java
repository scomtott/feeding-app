package com.example.springboot.homeassistant.jobs;

import java.time.LocalTime;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeOfDayBrightnessJob implements Job {

    private static final int BRIGHTNESS_TOLERANCE = 2;

    private static final LocalTime SEVEN_AM = LocalTime.of(7, 0);
    private static final LocalTime EIGHT_AM = LocalTime.of(8, 0);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);

    private static final double MORNING_STEEPNESS = 5.0;
    private static final double EVENING_STEEPNESS = 8.0;

    @Autowired
    private LightBrightnessService lightBrightnessService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            int targetBrightness = setBrightnessForTimeOfDay(
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
