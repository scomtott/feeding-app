package com.example.springboot.homeassistant.services;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.springboot.homeassistant.persistence.BathroomTelemetryEventRepository;
import com.example.springboot.homeassistant.persistence.BathroomTimelinePointRepository;
import com.example.springboot.homeassistant.persistence.entities.BathroomTelemetryEvent;
import com.example.springboot.homeassistant.persistence.entities.BathroomTelemetryEvent.SignalType;
import com.example.springboot.homeassistant.persistence.entities.BathroomTimelinePoint;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BathroomTelemetryStorageService {

    private static final String ENTITY_BATHROOM_LIGHT_1 = "light.bathroom_1";
    private static final String ENTITY_BATHROOM_LIGHT_2 = "light.bathroom_2";
    private static final String ENTITY_BATHROOM_GLEDOPTO = "light.gledopto_light";
    private static final String ENTITY_BATHROOM_OCCUPANCY = "binary_sensor.bathroom_motion_sensor_occupancy";
    private static final String ENTITY_BATHROOM_ILLUMINANCE = "sensor.bathroom_motion_sensor_illuminance";

    @Value("${homeassistant.telemetry.database-logging-enabled:true}")
    private boolean databaseLoggingEnabled;

    private final BathroomTelemetryEventRepository bathroomTelemetryEventRepository;
    private final BathroomTimelinePointRepository bathroomTimelinePointRepository;

    @Transactional
    public BathroomTelemetryEvent storeLightStateEvent(String entityId, boolean isOn, Instant tsUtc, String source, String payloadJson) {
        Instant ts = normalizeTs(tsUtc);

        BathroomTelemetryEvent event = new BathroomTelemetryEvent(
            ts,
            entityId,
            SignalType.LIGHT_STATE,
            isOn,
            null,
            isOn ? "on" : "off",
            source,
            payloadJson
        );

        if (!databaseLoggingEnabled) {
            return event;
        }

        BathroomTelemetryEvent stored = bathroomTelemetryEventRepository.save(event);

        appendTimelinePoint(ts, null, null, entityId, isOn);
        return stored;
    }

    @Transactional
    public BathroomTelemetryEvent storeOccupancyEvent(boolean occupied, Instant tsUtc, String source, String payloadJson) {
        Instant ts = normalizeTs(tsUtc);

        BathroomTelemetryEvent event = new BathroomTelemetryEvent(
            ts,
            ENTITY_BATHROOM_OCCUPANCY,
            SignalType.OCCUPANCY_STATE,
            occupied,
            null,
            occupied ? "on" : "off",
            source,
            payloadJson
        );

        if (!databaseLoggingEnabled) {
            return event;
        }

        BathroomTelemetryEvent stored = bathroomTelemetryEventRepository.save(event);

        appendTimelinePoint(ts, occupied, null, null, null);
        return stored;
    }

    @Transactional
    public BathroomTelemetryEvent storeIlluminanceEvent(double illuminanceLux, Instant tsUtc, String source, String payloadJson) {
        Instant ts = normalizeTs(tsUtc);

        BathroomTelemetryEvent event = new BathroomTelemetryEvent(
            ts,
            ENTITY_BATHROOM_ILLUMINANCE,
            SignalType.ILLUMINANCE,
            null,
            illuminanceLux,
            Double.toString(illuminanceLux),
            source,
            payloadJson
        );

        if (!databaseLoggingEnabled) {
            return event;
        }

        BathroomTelemetryEvent stored = bathroomTelemetryEventRepository.save(event);

        appendTimelinePoint(ts, null, illuminanceLux, null, null);
        return stored;
    }

    @Transactional
    public BathroomTimelinePoint storeTimelinePoint(
        Instant tsUtc,
        Boolean occupancy,
        Double illuminanceLux,
        Boolean bathroom1On,
        Boolean bathroom2On,
        Boolean gledoptoOn
    ) {
        BathroomTimelinePoint point = new BathroomTimelinePoint(
            normalizeTs(tsUtc),
            occupancy,
            illuminanceLux,
            bathroom1On,
            bathroom2On,
            gledoptoOn
        );

        if (!databaseLoggingEnabled) {
            return point;
        }

        return bathroomTimelinePointRepository.save(point);
    }

    public boolean isTrackedBathroomLight(String entityId) {
        return ENTITY_BATHROOM_LIGHT_1.equals(entityId)
            || ENTITY_BATHROOM_LIGHT_2.equals(entityId)
            || ENTITY_BATHROOM_GLEDOPTO.equals(entityId);
    }

    public boolean isTrackedOccupancySensor(String entityId) {
        return ENTITY_BATHROOM_OCCUPANCY.equals(entityId);
    }

    public boolean isTrackedIlluminanceSensor(String entityId) {
        return ENTITY_BATHROOM_ILLUMINANCE.equals(entityId);
    }

    private Instant normalizeTs(Instant tsUtc) {
        return tsUtc == null ? Instant.now() : tsUtc;
    }

    private void appendTimelinePoint(
        Instant tsUtc,
        Boolean occupancy,
        Double illuminanceLux,
        String lightEntityId,
        Boolean lightState
    ) {
        if (!databaseLoggingEnabled) {
            return;
        }

        BathroomTimelinePoint latest = bathroomTimelinePointRepository.findTopByOrderByTsUtcDesc();

        Boolean nextOccupancy = occupancy != null
            ? occupancy
            : latest != null ? latest.getOccupancy() : null;

        Double nextIlluminanceLux = illuminanceLux != null
            ? illuminanceLux
            : latest != null ? latest.getIlluminanceLux() : null;

        Boolean nextBathroom1 = latest != null ? latest.getLightBathroom1On() : null;
        Boolean nextBathroom2 = latest != null ? latest.getLightBathroom2On() : null;
        Boolean nextGledopto = latest != null ? latest.getLightGledoptoOn() : null;

        if (lightEntityId != null && lightState != null) {
            if (ENTITY_BATHROOM_LIGHT_1.equals(lightEntityId)) {
                nextBathroom1 = lightState;
            } else if (ENTITY_BATHROOM_LIGHT_2.equals(lightEntityId)) {
                nextBathroom2 = lightState;
            } else if (ENTITY_BATHROOM_GLEDOPTO.equals(lightEntityId)) {
                nextGledopto = lightState;
            }
        }

        bathroomTimelinePointRepository.save(new BathroomTimelinePoint(
            tsUtc,
            nextOccupancy,
            nextIlluminanceLux,
            nextBathroom1,
            nextBathroom2,
            nextGledopto
        ));
    }
}
