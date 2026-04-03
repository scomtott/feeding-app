package com.example.springboot.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import com.example.springboot.models.home_assistant.LightEntity;
import com.example.springboot.services.LightBrightnessService;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/homeassistant")
@RequiredArgsConstructor
@Slf4j
public class HomeAssistantController {

    private final LightBrightnessService lightBrightnessService;

    @GetMapping("/lights")
    public List<LightEntity> getLightEntities() {
        return lightBrightnessService.getLightEntities();
    }

    @GetMapping("/lights/{entityId:.+}")
    public LightEntity getLightEntity(@PathVariable String entityId) {
        return lightBrightnessService.getLightEntity(entityId);
    }

    @PostMapping("/lights/on")
    public void turnOnLight(
        @RequestParam("entity_id") String entityId,
        @RequestParam(value = "brightness", required = false) Integer brightness,
        @RequestParam(value = "rgb_color", required = false) List<Integer> rgbColor
    ) {
        log.info("Turning on light with entityId: {} brightness={} rgbColor={}", entityId, brightness, rgbColor);
        lightBrightnessService.turnOnLight(entityId, brightness, rgbColor);
    }

    @PostMapping("/lights/off")
    public void turnOffLight(@RequestParam("entity_id") String entityId) {
        log.info("Turning off light with entityId: {}", entityId);
        lightBrightnessService.turnOffLight(entityId);
    }

    @PostMapping("/lights/brightness")
    public void setBrightness(@RequestParam("entity_id") String entityId, @RequestParam("brightness") int brightness) {
        log.info("Setting brightness of light with entityId: {} to {}", entityId, brightness);
        lightBrightnessService.setBrightness(entityId, brightness);
    }

    @PostMapping("/lights/color")
    public void setColor(@RequestParam("entity_id") String entityId, @RequestParam("rgb_color") List<Integer> rgbColor) {
        log.info("Setting color of light with entityId: {} to {}", entityId, rgbColor);
        lightBrightnessService.setColor(entityId, rgbColor);
    }
}
