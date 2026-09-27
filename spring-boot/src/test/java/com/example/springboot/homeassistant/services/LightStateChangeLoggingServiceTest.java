package com.example.springboot.homeassistant.services;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.example.springboot.homeassistant.events.LightStateChangedEvent;
import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.logging.BufferedBackendFileLogger;
import com.example.springboot.models.LogLevel;

class LightStateChangeLoggingServiceTest {

    @Test
    void emitsLogWhenLightTurnsOnOrOff() {
        BufferedBackendFileLogger backendFileLogger = mock(BufferedBackendFileLogger.class);
        LightStateChangeLoggingService service = new LightStateChangeLoggingService(backendFileLogger);

        service.handleLightStateChanged(event(light("light.kitchen", "off"), light("light.kitchen", "on")));

        verify(backendFileLogger).log(
            eq(LightStateChangeLoggingService.class),
            eq(LogLevel.INFO),
            eq("light-state"),
            contains("light.kitchen changed state from off to on")
        );
    }

    @Test
    void doesNotEmitLogWhenStateDoesNotChange() {
        BufferedBackendFileLogger backendFileLogger = mock(BufferedBackendFileLogger.class);
        LightStateChangeLoggingService service = new LightStateChangeLoggingService(backendFileLogger);

        service.handleLightStateChanged(event(light("light.kitchen", "on"), light("light.kitchen", "on")));

        verifyNoInteractions(backendFileLogger);
    }

    private static LightStateChangedEvent event(LightEntity oldState, LightEntity newState) {
        return new LightStateChangedEvent("light.kitchen", oldState, newState);
    }

    private static LightEntity light(String entityId, String state) {
        return new LightEntity(entityId, state, null, null, null, null);
    }
}
