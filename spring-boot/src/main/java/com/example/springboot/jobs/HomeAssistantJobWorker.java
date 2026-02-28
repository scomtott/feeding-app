package com.example.springboot.jobs;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.springboot.models.home_assistant.LightEntity;
import com.example.springboot.services.HomeAssistantService;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HomeAssistantJobWorker {
    private final HomeAssistantService homeAssistantService;

    private final String[] lightEntityIds = {
        "kitchen_front",
        "hall",
        "kitchen_back",
        "upstairs_1",
        "landing_stairs",
        "0x001788010b2befca",
        "0x001788010b305217",
        "0x001788010b31352b",
        "0x001788010b31353c",
        "0x001788010b30661c",
        "0x001788010b30a7c1",
        "wiz_tunable_white_e60b0a"
    };

    public HomeAssistantJobWorker(HomeAssistantService homeAssistantService) {
        this.homeAssistantService = homeAssistantService;
    }

    @Async
    public void doWork() {
        log.info("Starting Home Assistant job worker.");
        try {
            List<LightEntity> lights = homeAssistantService.getLightEntities();
        } 
        catch (Exception e) {
            log.error("Error occurred in Home Assistant job worker", e);
        }
    }
}