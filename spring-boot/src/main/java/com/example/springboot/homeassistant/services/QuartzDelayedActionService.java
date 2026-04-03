package com.example.springboot.homeassistant.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import com.example.springboot.homeassistant.jobs.DelayedLightOffJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuartzDelayedActionService implements DelayedActionService {

    private static final String GROUP = "delayed-actions";

    private final Scheduler scheduler;

    @Override
    public void scheduleTurnOffLight(String actionKey, String lightEntityId, Duration delay) {
        if (actionKey == null || actionKey.isBlank() || lightEntityId == null || lightEntityId.isBlank()) {
            throw new IllegalArgumentException("actionKey and lightEntityId are required");
        }

        Duration safeDelay = delay == null || delay.isNegative() ? Duration.ZERO : delay;
        Instant fireTime = Instant.now().plus(safeDelay);

        JobKey jobKey = JobKey.jobKey(actionKey, GROUP);
        TriggerKey triggerKey = TriggerKey.triggerKey(actionKey, GROUP);

        JobDetail jobDetail = JobBuilder.newJob(DelayedLightOffJob.class)
            .withIdentity(jobKey)
            .usingJobData(DelayedLightOffJob.LIGHT_ENTITY_ID_KEY, lightEntityId)
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .forJob(jobKey)
            .startAt(Date.from(fireTime))
            .build();

        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled delayed action key={} light={} at={} (delay={}s)", actionKey, lightEntityId, fireTime, safeDelay.toSeconds());
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to schedule delayed action: " + actionKey, e);
        }
    }

    @Override
    public void cancel(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            return;
        }

        JobKey jobKey = JobKey.jobKey(actionKey, GROUP);
        try {
            if (scheduler.deleteJob(jobKey)) {
                log.info("Cancelled delayed action key={}", actionKey);
            }
            else {
                log.info("No delayed action found to cancel for key={}", actionKey);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to cancel delayed action: " + actionKey, e);
        }
    }
}
