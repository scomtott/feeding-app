package com.example.springboot.homeassistant.persistence.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "bathroom_telemetry_event",
    indexes = {
        @Index(name = "idx_bathroom_event_ts", columnList = "ts_utc"),
        @Index(name = "idx_bathroom_event_entity_ts", columnList = "entity_id,ts_utc"),
        @Index(name = "idx_bathroom_event_signal_ts", columnList = "signal_type,ts_utc")
    }
)
public class BathroomTelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private SignalType signalType;

    @Column(name = "value_bool")
    private Boolean valueBool;

    @Column(name = "value_num")
    private Double valueNum;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "source")
    private String source;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    public BathroomTelemetryEvent() {
    }

    public BathroomTelemetryEvent(
        Instant tsUtc,
        String entityId,
        SignalType signalType,
        Boolean valueBool,
        Double valueNum,
        String valueText,
        String source,
        String payloadJson
    ) {
        this.tsUtc = tsUtc;
        this.entityId = entityId;
        this.signalType = signalType;
        this.valueBool = valueBool;
        this.valueNum = valueNum;
        this.valueText = valueText;
        this.source = source;
        this.payloadJson = payloadJson;
    }

    public Long getId() {
        return id;
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public String getEntityId() {
        return entityId;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public Boolean getValueBool() {
        return valueBool;
    }

    public Double getValueNum() {
        return valueNum;
    }

    public String getValueText() {
        return valueText;
    }

    public String getSource() {
        return source;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public enum SignalType {
        LIGHT_STATE,
        OCCUPANCY_STATE,
        ILLUMINANCE
    }
}
