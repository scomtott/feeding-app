package com.example.springboot.homeassistant.jobs;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogOnLightsJobScheduler {

    private static final String GROUP = "homeassistant-monitoring";
    private static final JobKey JOB_KEY = JobKey.jobKey("log-on-lights", GROUP);
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("log-on-lights-trigger", GROUP);

    private final Scheduler scheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleOnStartup() {
        try {
            if (scheduler.checkExists(JOB_KEY)) {
                log.info("Quartz job {} is already scheduled.", JOB_KEY);
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(LogOnLightsJob.class)
                .withIdentity(JOB_KEY)
                .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_KEY)
                .forJob(JOB_KEY)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(10)
                    .repeatForever())
                .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled Quartz job {} to run every 10 seconds.", JOB_KEY);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to schedule 10-second on-lights job", e);
        }
    }
}
