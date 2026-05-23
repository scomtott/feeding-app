package com.example.springboot.homeassistant.jobs;

import java.time.Duration;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.services.DelayedActionService;
import com.example.springboot.homeassistant.services.LightBrightnessService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelayedLightOffJob implements Job {

    public static final String LIGHT_ENTITY_ID_KEY = "lightEntityId";
    public static final String PHASE_KEY = "phase";
    public static final String ATTEMPT_KEY = "attempt";
    public static final String ROOT_SCHEDULED_AT_EPOCH_MS_KEY = "rootScheduledAtEpochMs";

    public static final String REQUEST_PHASE = "request_off";
    public static final String VERIFY_PHASE = "verify_off";

    private static final Duration INITIAL_VERIFY_DELAY = Duration.ofSeconds(10);
    private static final Duration MAX_VERIFY_DELAY = Duration.ofMinutes(5);
    private static final int MAX_VERIFY_RETRIES = 5;

    @Autowired
    private LightBrightnessService lightBrightnessService;

    @Autowired
    private DelayedActionService delayedActionService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String lightEntityId = dataMap.getString(LIGHT_ENTITY_ID_KEY);
        String phase = dataMap.getString(PHASE_KEY);
        int attempt = Math.max(0, dataMap.getIntValue(ATTEMPT_KEY));
        long rootScheduledAtEpochMs = dataMap.containsKey(ROOT_SCHEDULED_AT_EPOCH_MS_KEY)
            ? dataMap.getLongValue(ROOT_SCHEDULED_AT_EPOCH_MS_KEY)
            : System.currentTimeMillis();
        String actionKey = context.getJobDetail().getKey().getName();

        if (lightEntityId == null || lightEntityId.isBlank()) {
            log.warn("Skipping delayed light off action with missing lightEntityId");
            return;
        }

        String effectivePhase = phase == null || phase.isBlank() ? REQUEST_PHASE : phase;

        try {
            if (REQUEST_PHASE.equals(effectivePhase)) {
                lightBrightnessService.turnOffLight(lightEntityId);
                delayedActionService.scheduleTurnOffLightVerification(
                    actionKey,
                    lightEntityId,
                    INITIAL_VERIFY_DELAY,
                    0,
                    rootScheduledAtEpochMs
                );
                log.info(
                    "Executed delayed action request for light {}. Verification scheduled in {}s.",
                    lightEntityId,
                    INITIAL_VERIFY_DELAY.toSeconds()
                );
                return;
            }

            if (!VERIFY_PHASE.equals(effectivePhase)) {
                log.warn("Skipping delayed light off action with unsupported phase {} for light {}", effectivePhase, lightEntityId);
                return;
            }

            if (isLightOff(lightEntityId)) {
                log.info("Verified delayed turn-off succeeded for light {}", lightEntityId);
                return;
            }

            if (attempt >= MAX_VERIFY_RETRIES) {
                log.warn(
                    "Delayed turn-off verification exhausted retries for light {} after {} attempt(s)",
                    lightEntityId,
                    attempt
                );
                return;
            }

            int nextAttempt = attempt + 1;
            Duration nextVerifyDelay = backoffDelay(nextAttempt);

            lightBrightnessService.turnOffLight(lightEntityId);
            delayedActionService.scheduleTurnOffLightVerification(
                actionKey,
                lightEntityId,
                nextVerifyDelay,
                nextAttempt,
                rootScheduledAtEpochMs
            );
            log.warn(
                "Light {} still on during delayed off verification attempt {}. Reissued off and scheduled next verification in {}s.",
                lightEntityId,
                nextAttempt,
                nextVerifyDelay.toSeconds()
            );
        } catch (RuntimeException e) {
            throw new JobExecutionException("Failed to execute delayed turn-off for light: " + lightEntityId, e, false);
        }
    }

    private boolean isLightOff(String lightEntityId) {
        LightEntity light = lightBrightnessService.getLightEntity(lightEntityId);
        return light != null && "off".equalsIgnoreCase(light.state());
    }

    private Duration backoffDelay(int attempt) {
        long exponentialMultiplier = 1L << Math.min(attempt, 10);
        Duration delay = INITIAL_VERIFY_DELAY.multipliedBy(exponentialMultiplier);
        return delay.compareTo(MAX_VERIFY_DELAY) > 0 ? MAX_VERIFY_DELAY : delay;
    }
}
