package com.example.springboot.homeassistant.jobs;

import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.models.LightEntityClassifier;
import com.example.springboot.homeassistant.services.LightBrightnessService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogOnLightsJob implements Job {

    private static volatile boolean loggingEnabled = false;

    @Autowired
    private LightBrightnessService lightBrightnessService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            List<LightEntity> onLights = lightBrightnessService.getLightEntities().stream()
                .filter(light -> "on".equalsIgnoreCase(light.state()))
                .toList();

            if (onLights.isEmpty()) {
                logInfoIfEnabled("No lights are currently turned on.");
                return;
            }

            String formattedLights = onLights.stream()
                .map(light -> {
                    String friendlyName = light.attributes() != null ? light.attributes().friendlyName() : null;
                    String displayName = friendlyName != null && !friendlyName.isBlank() ? friendlyName : light.entityId();
                    return String.format(
                        "- %s (%s) [%s]",
                        displayName,
                        light.entityId(),
                        LightEntityClassifier.classify(light)
                    );
                })
                .collect(java.util.stream.Collectors.joining("\n"));

            logInfoIfEnabled("Lights currently turned on ({}):\n{}", onLights.size(), formattedLights);
        } catch (RuntimeException e) {
            throw new JobExecutionException("Failed to list currently-on lights", e, false);
        }
    }

    private void logInfoIfEnabled(String message, Object... args) {
        if (loggingEnabled) {
            log.info(message, args);
        }
    }
}
