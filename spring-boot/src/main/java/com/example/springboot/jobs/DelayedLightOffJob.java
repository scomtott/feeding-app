package com.example.springboot.jobs;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.springboot.services.LightBrightnessService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelayedLightOffJob implements Job {

    public static final String LIGHT_ENTITY_ID_KEY = "lightEntityId";

    @Autowired
    private LightBrightnessService lightBrightnessService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String lightEntityId = dataMap.getString(LIGHT_ENTITY_ID_KEY);

        if (lightEntityId == null || lightEntityId.isBlank()) {
            log.warn("Skipping delayed light off action with missing lightEntityId");
            return;
        }

        try {
            lightBrightnessService.turnOffLight(lightEntityId);
            log.info("Executed delayed action: turned off light {}", lightEntityId);
        } catch (RuntimeException e) {
            throw new JobExecutionException("Failed to execute delayed turn-off for light: " + lightEntityId, e, false);
        }
    }
}
