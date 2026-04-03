package com.example.springboot.homeassistant.services;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.springboot.homeassistant.automations.events.OccupancyStateChangedEvent;
import com.example.springboot.homeassistant.models.BinarySensorEntity;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class BinarySensorOccupancyService {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    public void handleOccupancySensorStateChanged(HaWsStateChangedEvent<BinarySensorEntity> event) {
        try {
            String prettyEvent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
            log.info("Handling occupancy sensor state changed event:\n{}", prettyEvent);
        } catch (RuntimeException e) {
            log.warn("Handling occupancy sensor state changed event (failed to pretty serialize): {}", event, e);
        }

        if (event.event() == null || event.event().data() == null) {
            return;
        }

        BinarySensorEntity oldState = event.event().data().oldState();
        BinarySensorEntity newState = event.event().data().newState();

        if (oldState == null || newState == null) {
            return;
        }

        boolean oldIsOn = oldState.isOn();
        boolean newIsOn = newState.isOn();

        if (oldIsOn == newIsOn) {
            return;
        }

        String entityId = newState.entityId() != null ? newState.entityId() : "unknown";
        String status = newIsOn ? "ON (detected)" : "OFF (clear)";
        log.info("Occupancy sensor {} changed to {}", entityId, status);
        eventPublisher.publishEvent(new OccupancyStateChangedEvent(entityId, newIsOn));
    }
}
