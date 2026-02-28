package com.example.springboot.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.springboot.clients.HomeAssistantHttpClient;
import com.example.springboot.models.home_assistant.LightEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomeAssistantService {

    private final HomeAssistantHttpClient homeAssistantHttpClient;

    public List<LightEntity> getLightEntities() {
        return homeAssistantHttpClient.getLightEntities();
    }

    public LightEntity getLightEntity(String entityId) {
        return homeAssistantHttpClient.getLightEntity(entityId);
    }

    public void turnOnLight(String entityId) {
        homeAssistantHttpClient.turnOnLight(entityId);
    }

    public void turnOffLight(String entityId) {
        homeAssistantHttpClient.turnOffLight(entityId);
    }

    public void setBrightness(String entityId, int brightness) {
        homeAssistantHttpClient.setBrightness(entityId, brightness);
    }
}