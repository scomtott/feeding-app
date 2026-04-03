package com.example.springboot.homeassistant.services;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.example.springboot.homeassistant.models.BinarySensorEntity;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BinarySensorService {

    private final Map<String, Consumer<HaWsStateChangedEvent<BinarySensorEntity>>> deviceClassHandlers;

    public BinarySensorService(BinarySensorOccupancyService occupancyService) {
        this.deviceClassHandlers = new HashMap<>();
        
        // Register device class handlers
        deviceClassHandlers.put("occupancy", occupancyService::handleOccupancySensorStateChanged);
    }

    public void handleBinarySensorStateChanged(HaWsStateChangedEvent<BinarySensorEntity> event) {
        if (event.event() == null || event.event().data() == null || event.event().data().newState() == null) {
            return;
        }

        BinarySensorEntity entity = event.event().data().newState();
        String deviceClass = entity.attributes() != null ? entity.attributes().deviceClass() : null;

        if (deviceClass == null) {
            log.debug("Binary sensor without device class: {}", entity.entityId());
            return;
        }

        Consumer<HaWsStateChangedEvent<BinarySensorEntity>> handler = deviceClassHandlers.get(deviceClass);
        if (handler == null) {
            log.debug("No handler registered for binary_sensor device_class: {}", deviceClass);
            return;
        }

        handler.accept(event);
    }
}
