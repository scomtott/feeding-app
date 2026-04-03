package com.example.springboot.homeassistant.services;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.springboot.homeassistant.models.SensorEntity;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class IlluminanceSensorService {

    private final ObjectMapper objectMapper;

    @Async
    public void handleIlluminanceSensorStateChanged(HaWsStateChangedEvent<SensorEntity> event) {
        try {
            // Skip logging illuminance sensor events.
            //String prettyEvent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
            //log.info("Handling illuminance sensor state changed event:\n{}", prettyEvent);
        } catch (RuntimeException e) {
            log.warn("Handling illuminance sensor state changed event (failed to pretty serialize): {}", event, e);
        }

        if (event.event() == null || event.event().data() == null) {
            return;
        }

        SensorEntity newState = event.event().data().newState();

        if (newState == null) {
            return;
        }

        Double illuminance = newState.getNumericalValue();
        if (illuminance == null) {
            return;
        }

        String entityId = newState.entityId() != null ? newState.entityId() : "unknown";
        log.info("Illuminance sensor {} reading: {} lx", entityId, illuminance);
    }
}
