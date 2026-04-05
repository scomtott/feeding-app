package com.example.springboot.homeassistant.persistence.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "bathroom_timeline_point",
    indexes = {
        @Index(name = "idx_bathroom_timeline_ts", columnList = "ts_utc")
    }
)
public class BathroomTimelinePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ts_utc", nullable = false)
    private Instant tsUtc;

    @Column(name = "occupancy")
    private Boolean occupancy;

    @Column(name = "illuminance_lux")
    private Double illuminanceLux;

    @Column(name = "light_bathroom_1_on")
    private Boolean lightBathroom1On;

    @Column(name = "light_bathroom_2_on")
    private Boolean lightBathroom2On;

    @Column(name = "light_gledopto_on")
    private Boolean lightGledoptoOn;

    public BathroomTimelinePoint() {
    }

    public BathroomTimelinePoint(
        Instant tsUtc,
        Boolean occupancy,
        Double illuminanceLux,
        Boolean lightBathroom1On,
        Boolean lightBathroom2On,
        Boolean lightGledoptoOn
    ) {
        this.tsUtc = tsUtc;
        this.occupancy = occupancy;
        this.illuminanceLux = illuminanceLux;
        this.lightBathroom1On = lightBathroom1On;
        this.lightBathroom2On = lightBathroom2On;
        this.lightGledoptoOn = lightGledoptoOn;
    }

    public Long getId() {
        return id;
    }

    public Instant getTsUtc() {
        return tsUtc;
    }

    public Boolean getOccupancy() {
        return occupancy;
    }

    public Double getIlluminanceLux() {
        return illuminanceLux;
    }

    public Boolean getLightBathroom1On() {
        return lightBathroom1On;
    }

    public Boolean getLightBathroom2On() {
        return lightBathroom2On;
    }

    public Boolean getLightGledoptoOn() {
        return lightGledoptoOn;
    }
}
