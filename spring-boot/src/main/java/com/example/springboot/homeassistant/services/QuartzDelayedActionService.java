package com.example.springboot.homeassistant.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
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
        long rootScheduledAtEpochMs = Instant.now().toEpochMilli();
        scheduleJob(actionKey, lightEntityId, safeDelay, DelayedLightOffJob.REQUEST_PHASE, 0, rootScheduledAtEpochMs);
    }

    @Override
    public void scheduleTurnOffLightVerification(String actionKey, String lightEntityId, Duration delay, int attempt, long rootScheduledAtEpochMs) {
        if (actionKey == null || actionKey.isBlank() || lightEntityId == null || lightEntityId.isBlank()) {
            throw new IllegalArgumentException("actionKey and lightEntityId are required");
        }

        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must be >= 0");
        }

        Duration safeDelay = delay == null || delay.isNegative() ? Duration.ZERO : delay;
        scheduleJob(actionKey, lightEntityId, safeDelay, DelayedLightOffJob.VERIFY_PHASE, attempt, rootScheduledAtEpochMs);
    }

    private void scheduleJob(String actionKey, String lightEntityId, Duration delay, String phase, int attempt, long rootScheduledAtEpochMs) {
        Instant fireTime = Instant.now().plus(delay);
        String normalizedLightEntityId = normalizeLightEntityId(lightEntityId);

        JobKey jobKey = JobKey.jobKey(actionKey, GROUP);
        TriggerKey triggerKey = TriggerKey.triggerKey(actionKey, GROUP);

        JobDetail jobDetail = JobBuilder.newJob(DelayedLightOffJob.class)
            .withIdentity(jobKey)
            .usingJobData(DelayedLightOffJob.LIGHT_ENTITY_ID_KEY, normalizedLightEntityId)
            .usingJobData(DelayedLightOffJob.PHASE_KEY, phase)
            .usingJobData(DelayedLightOffJob.ATTEMPT_KEY, attempt)
            .usingJobData(DelayedLightOffJob.ROOT_SCHEDULED_AT_EPOCH_MS_KEY, rootScheduledAtEpochMs)
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
            log.info(
                "Scheduled delayed action key={} light={} phase={} attempt={} at={} (delay={}s)",
                actionKey,
                normalizedLightEntityId,
                phase,
                attempt,
                fireTime,
                delay.toSeconds()
            );
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

    @Override
    public void cancelTurnOffLightActions(String lightEntityId) {
        cancelTurnOffLightActionsScheduledBefore(lightEntityId, null);
    }

    @Override
    public void cancelTurnOffLightActionsScheduledBefore(String lightEntityId, Instant cutoff) {
        if (lightEntityId == null || lightEntityId.isBlank()) {
            return;
        }

        String normalizedLightEntityId = normalizeLightEntityId(lightEntityId);
        long cutoffEpochMs = cutoff != null ? cutoff.toEpochMilli() : Long.MAX_VALUE;

        try {
            Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP));
            int cancelled = 0;
            for (JobKey jobKey : jobKeys) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                if (jobDetail == null) {
                    continue;
                }

                String jobLightEntityId = jobDetail.getJobDataMap().getString(DelayedLightOffJob.LIGHT_ENTITY_ID_KEY);
                if (!normalizedLightEntityId.equalsIgnoreCase(normalizeLightEntityId(jobLightEntityId))) {
                    continue;
                }

                long jobRootScheduledAt = jobDetail.getJobDataMap().containsKey(DelayedLightOffJob.ROOT_SCHEDULED_AT_EPOCH_MS_KEY)
                    ? jobDetail.getJobDataMap().getLongValue(DelayedLightOffJob.ROOT_SCHEDULED_AT_EPOCH_MS_KEY)
                    : Long.MIN_VALUE;
                if (jobRootScheduledAt > cutoffEpochMs) {
                    continue;
                }

                if (scheduler.deleteJob(jobKey)) {
                    cancelled++;
                }
            }

            if (cancelled > 0) {
                log.info("Cancelled {} delayed turn-off action(s) for light {}", cancelled, normalizedLightEntityId);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to cancel delayed actions for light: " + normalizedLightEntityId, e);
        }
    }

    @Override
    public boolean isScheduled(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            return false;
        }

        JobKey jobKey = JobKey.jobKey(actionKey, GROUP);
        try {
            return scheduler.checkExists(jobKey);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to check delayed action: " + actionKey, e);
        }
    }

    @Override
    public List<PendingAction> getPendingActions() {
        try {
            Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP));
            return jobKeys.stream()
                .map(this::toPendingAction)
                .filter(action -> action != null)
                .sorted((left, right) -> {
                    Instant leftFire = left.nextFireTime();
                    Instant rightFire = right.nextFireTime();
                    if (leftFire == null && rightFire == null) {
                        return left.actionKey().compareTo(right.actionKey());
                    }
                    if (leftFire == null) {
                        return 1;
                    }
                    if (rightFire == null) {
                        return -1;
                    }
                    int comparison = leftFire.compareTo(rightFire);
                    return comparison != 0 ? comparison : left.actionKey().compareTo(right.actionKey());
                })
                .toList();
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to list delayed actions", e);
        }
    }

    private PendingAction toPendingAction(JobKey jobKey) {
        try {
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail == null) {
                return null;
            }

            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            Instant nextFireTime = triggers.stream()
                .map(Trigger::getNextFireTime)
                .filter(date -> date != null)
                .map(Date::toInstant)
                .min(Instant::compareTo)
                .orElse(null);

            String lightEntityId = jobDetail.getJobDataMap().getString(DelayedLightOffJob.LIGHT_ENTITY_ID_KEY);
            String phase = jobDetail.getJobDataMap().getString(DelayedLightOffJob.PHASE_KEY);
            int attempt = jobDetail.getJobDataMap().getIntValue(DelayedLightOffJob.ATTEMPT_KEY);
            long rootScheduledAtEpochMs = jobDetail.getJobDataMap().containsKey(DelayedLightOffJob.ROOT_SCHEDULED_AT_EPOCH_MS_KEY)
                ? jobDetail.getJobDataMap().getLongValue(DelayedLightOffJob.ROOT_SCHEDULED_AT_EPOCH_MS_KEY)
                : 0L;

            return new PendingAction(
                jobKey.getName(),
                lightEntityId,
                phase,
                Math.max(0, attempt),
                rootScheduledAtEpochMs,
                nextFireTime
            );
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to inspect delayed action: " + jobKey, e);
        }
    }

    private String normalizeLightEntityId(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return "";
        }
        return entityId.startsWith("light.") ? entityId : "light." + entityId;
    }
}