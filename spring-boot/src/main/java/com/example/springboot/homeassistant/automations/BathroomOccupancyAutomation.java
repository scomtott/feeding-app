package com.example.springboot.homeassistant.automations;

import java.time.LocalTime;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.springboot.homeassistant.automations.events.OccupancyStateChangedEvent;
import com.example.springboot.homeassistant.properties.BathroomOccupancyAutomationProperties;
import com.example.springboot.homeassistant.services.DelayedActionService;
import com.example.springboot.homeassistant.services.LightBrightnessService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BathroomOccupancyAutomation {

    private static final LocalTime EIGHT_AM = LocalTime.of(8, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);
    private static final List<Integer> RED_RGB_COLOR = List.of(255, 0, 0);

    private final BathroomOccupancyAutomationProperties properties;
    private final DelayedActionService delayedActionService;
    private final LightBrightnessService lightBrightnessService;

    @EventListener
    public void onOccupancyChanged(OccupancyStateChangedEvent event) {
        log.info("Received occupancy state changed event.");
        if (!properties.isEnabled()) {
            log.info("Bathroom occupancy automation is disabled. Ignoring event.");
            return;
        }

        String configuredSensor = properties.getSensorEntityId();
        String configuredLight = properties.getLightEntityId();
        if (configuredSensor == null || configuredSensor.isBlank() || configuredLight == null || configuredLight.isBlank()) {
            log.info("Bathroom occupancy automation is not properly configured. Ignoring event.");
            return;
        }

        if (!configuredSensor.equals(event.entityId())) {
            log.info("Received occupancy state changed event for sensor {}, but configured sensor is {}. Ignoring event.", event.entityId(), configuredSensor);
            return;
        }

        String actionKey = "automation:bathroom-occupancy:" + configuredSensor + ":" + configuredLight + ":turn_off";

        if (event.occupied()) {
            delayedActionService.cancel(actionKey);
            if (isWithinNormalLightingHours()) {
                lightBrightnessService.turnOnLightWhite(configuredLight, 3);
            } else {
                lightBrightnessService.turnOnLight(configuredLight, 3, RED_RGB_COLOR);
            }
            log.info("Bathroom occupancy detected for {}. Turned on {} and cancelled pending off action.", configuredSensor, configuredLight);
            delayedActionService.scheduleTurnOffLight(actionKey, configuredLight, properties.getOffDelay());
            return;
        }

        log.info("Bathroom occupancy clear for {}.", configuredSensor);
    }

    private boolean isWithinNormalLightingHours() {
        LocalTime now = LocalTime.now();
        return !now.isBefore(EIGHT_AM) && now.isBefore(NINE_PM);
    }
}
