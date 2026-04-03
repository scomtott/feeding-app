package com.example.springboot.homeassistant.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "automation.bathroom-occupancy")
public class BathroomOccupancyAutomationProperties {

    private boolean enabled = true;
    private String sensorEntityId;
    private String lightEntityId;
    private long offDelaySeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSensorEntityId() {
        return sensorEntityId;
    }

    public void setSensorEntityId(String sensorEntityId) {
        this.sensorEntityId = sensorEntityId;
    }

    public String getLightEntityId() {
        return lightEntityId;
    }

    public void setLightEntityId(String lightEntityId) {
        this.lightEntityId = lightEntityId;
    }

    public long getOffDelaySeconds() {
        return offDelaySeconds;
    }

    public void setOffDelaySeconds(long offDelaySeconds) {
        this.offDelaySeconds = offDelaySeconds;
    }

    public Duration getOffDelay() {
        return Duration.ofSeconds(Math.max(0, offDelaySeconds));
    }
}
