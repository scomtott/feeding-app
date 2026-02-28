package com.example.springboot.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomeAssistantJobTrigger {

    private final HomeAssistantJobWorker homeAssistantJobWorker;

    @Scheduled(fixedRate = 120000)
    public void setBrightnessLevels() {
        log.info("Setting brightness levels...");
        homeAssistantJobWorker.doWork();
    }
}