package com.example.springboot.homeassistant.services;

import java.time.Duration;

public interface DelayedActionService {

    void scheduleTurnOffLight(String actionKey, String lightEntityId, Duration delay);

    void cancel(String actionKey);
}
