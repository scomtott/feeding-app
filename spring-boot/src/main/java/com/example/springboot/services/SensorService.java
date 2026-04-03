package com.example.springboot.services;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.example.springboot.models.home_assistant.SensorEntity;
import com.example.springboot.websockets.messages.HaWsStateChangedEvent;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SensorService {

    private final Map<String, Consumer<HaWsStateChangedEvent<SensorEntity>>> deviceClassHandlers;

    public SensorService(IlluminanceSensorService illuminanceService) {
        this.deviceClassHandlers = new HashMap<>();
        
        // Register device class handlers
        deviceClassHandlers.put("illuminance", illuminanceService::handleIlluminanceSensorStateChanged);
    }

    public void handleSensorStateChanged(HaWsStateChangedEvent<SensorEntity> event) {
        if (event.event() == null || event.event().data() == null || event.event().data().newState() == null) {
            return;
        }

        SensorEntity entity = event.event().data().newState();
        String deviceClass = entity.attributes() != null ? entity.attributes().deviceClass() : null;

        if (deviceClass == null) {
            log.debug("Sensor without device class: {}", entity.entityId());
            return;
        }

        Consumer<HaWsStateChangedEvent<SensorEntity>> handler = deviceClassHandlers.get(deviceClass);
        if (handler == null) {
            log.debug("No handler registered for sensor device_class: {}", deviceClass);
            return;
        }

        handler.accept(event);
    }
}
