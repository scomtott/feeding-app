package com.example.springboot.homeassistant.services;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.example.springboot.homeassistant.events.BinarySensorStateChangedEvent;
import com.example.springboot.homeassistant.models.BinarySensorEntity;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BinarySensorService {

    private final Map<String, Consumer<BinarySensorStateChangedEvent>> deviceClassHandlers;

    public BinarySensorService(BinarySensorOccupancyService occupancyService) {
        this.deviceClassHandlers = new HashMap<>();
        
        // Register device class handlers
        deviceClassHandlers.put("occupancy", occupancyService::handleOccupancySensorStateChanged);
    }

    public void handleBinarySensorStateChanged(BinarySensorStateChangedEvent event) {
        BinarySensorEntity entity = event.newState();
        if (entity == null) {
            return;
        }

        String deviceClass = event.deviceClass();
        if (deviceClass == null) {
            log.debug("Binary sensor without device class: {}", entity.entityId());
            return;
        }

        Consumer<BinarySensorStateChangedEvent> handler = deviceClassHandlers.get(deviceClass);
        if (handler == null) {
            log.debug("No handler registered for binary_sensor device_class: {}", deviceClass);
            return;
        }

        handler.accept(event);
    }
}
