package com.example.springboot.homeassistant.services;

import com.example.springboot.homeassistant.events.LightStateChangedEvent;
import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.logging.BufferedBackendFileLogger;
import com.example.springboot.models.LogLevel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LightStateChangeLoggingService {

    private final BufferedBackendFileLogger backendFileLogger;

    public void handleLightStateChanged(LightStateChangedEvent event) {
        if (event == null) {
            return;
        }

        LightEntity oldState = event.oldState();
        LightEntity newState = event.newState();
        if (oldState == null || newState == null) {
            return;
        }

        String oldValue = oldState.state();
        String newValue = newState.state();
        if (oldValue == null || newValue == null) {
            return;
        }

        boolean oldOn = "on".equalsIgnoreCase(oldValue);
        boolean oldOff = "off".equalsIgnoreCase(oldValue);
        boolean newOn = "on".equalsIgnoreCase(newValue);
        boolean newOff = "off".equalsIgnoreCase(newValue);

        if (!(oldOn || oldOff) || !(newOn || newOff) || oldOn == newOn) {
            return;
        }

        String entityId = newState.entityId() == null || newState.entityId().isBlank()
            ? "unknown"
            : newState.entityId();
        backendFileLogger.log(
            LightStateChangeLoggingService.class,
            LogLevel.INFO,
            "light-state",
            "Light " + entityId + " changed state from " + (oldOn ? "on" : "off") + " to " + (newOn ? "on" : "off")
        );
    }
}
