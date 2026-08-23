package com.example.springboot.homeassistant.automations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.springboot.homeassistant.automations.events.OccupancyStateChangedEvent;
import com.example.springboot.homeassistant.client.HomeAssistantHttpClient;
import com.example.springboot.homeassistant.models.LightEntity;
import com.example.springboot.homeassistant.properties.BathroomOccupancyAutomationProperties;
import com.example.springboot.homeassistant.services.DelayedActionService;
import com.example.springboot.homeassistant.services.LightBrightnessService;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedData;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEvent;
import com.example.springboot.homeassistant.websocket.messages.HaWsStateChangedEventPayload;

import tools.jackson.databind.ObjectMapper;

class BathroomOccupancyAutomationTest {

    private static final String SENSOR_ENTITY_ID = "binary_sensor.bathroom_motion_sensor_occupancy";
    private static final String LIGHT_ENTITY_ID = "light.gledopto_light";

    private BathroomOccupancyAutomation automation;
    private DelayedActionService delayedActionService;
    private LightBrightnessService lightBrightnessService;

    @BeforeEach
    void setUp() {
        BathroomOccupancyAutomationProperties properties = new BathroomOccupancyAutomationProperties();
        properties.setEnabled(true);
        properties.setSensorEntityId(SENSOR_ENTITY_ID);
        properties.setLightEntityId(LIGHT_ENTITY_ID);
        properties.setOffDelaySeconds(300);

        delayedActionService = mock(DelayedActionService.class);
        lightBrightnessService = mock(LightBrightnessService.class);
        HomeAssistantHttpClient homeAssistantHttpClient = mock(HomeAssistantHttpClient.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);

        automation = new BathroomOccupancyAutomation(
            properties,
            delayedActionService,
            lightBrightnessService,
            homeAssistantHttpClient,
            objectMapper
        );
    }

    @Test
    void manualTurnOnActivatesLeaseAndSuppressesOccupancyAutomation() {
        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("off"),
            lightState("on")
        ));

        String actionKey = actionKey();
        verify(delayedActionService).cancel(actionKey);

        reset(delayedActionService, lightBrightnessService);

        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        verify(delayedActionService).cancel(actionKey);
        verify(delayedActionService, never()).scheduleTurnOffLight(actionKey, LIGHT_ENTITY_ID, Duration.ofSeconds(300));
        verifyNoInteractions(lightBrightnessService);
    }

    @Test
    void leaseClearsWhenLightTurnsOffAndMotionAutomationResumes() {
        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("off"),
            lightState("on")
        ));

        reset(delayedActionService, lightBrightnessService);

        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("on"),
            lightState("off")
        ));

        reset(delayedActionService, lightBrightnessService);

        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        String actionKey = actionKey();
        verify(delayedActionService).cancel(actionKey);
        verify(delayedActionService).scheduleTurnOffLight(actionKey, LIGHT_ENTITY_ID, Duration.ofSeconds(300));
    }

    @Test
    void manualTurnOnWithMissingOldStateStillActivatesLease() {
        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            null,
            lightState("on")
        ));

        String actionKey = actionKey();
        verify(delayedActionService).cancel(actionKey);
        assertNotNull(automation.getActiveManualLease());

        reset(delayedActionService, lightBrightnessService);

        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        verify(delayedActionService).cancel(actionKey);
        verify(delayedActionService, never()).scheduleTurnOffLight(actionKey, LIGHT_ENTITY_ID, Duration.ofSeconds(300));
        verifyNoInteractions(lightBrightnessService);
    }

    @Test
    void leaseClearsWhenTurnOffEventHasMissingOldState() {
        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("off"),
            lightState("on")
        ));
        assertNotNull(automation.getActiveManualLease());

        reset(delayedActionService, lightBrightnessService);

        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            null,
            lightState("off")
        ));
        assertNull(automation.getActiveManualLease());

        reset(delayedActionService, lightBrightnessService);

        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        String actionKey = actionKey();
        verify(delayedActionService).cancel(actionKey);
        verify(delayedActionService).scheduleTurnOffLight(actionKey, LIGHT_ENTITY_ID, Duration.ofSeconds(300));
    }

    @Test
    void occupancyTriggeredTurnOnDoesNotActivateManualLease() {
        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        reset(delayedActionService, lightBrightnessService);

        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("off"),
            lightState("on")
        ));

        reset(delayedActionService, lightBrightnessService);

        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        String actionKey = actionKey();
        verify(delayedActionService).cancel(actionKey);
        verify(delayedActionService).scheduleTurnOffLight(actionKey, LIGHT_ENTITY_ID, Duration.ofSeconds(300));
    }

    @Test
    void manualTurnOnWhileAlreadyOnActivatesLeaseWhenMotionOffIsScheduled() {
        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("on", "motion-context"),
            lightState("on", "motion-context")
        ));

        reset(delayedActionService, lightBrightnessService);
        when(delayedActionService.isScheduled(actionKey())).thenReturn(true);

        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("on", "motion-context"),
            lightState("on", "manual-context")
        ));

        verify(delayedActionService).cancel(actionKey());

        reset(delayedActionService, lightBrightnessService);

        automation.onOccupancyChanged(new OccupancyStateChangedEvent(SENSOR_ENTITY_ID, true));

        verify(delayedActionService).cancel(actionKey());
        verify(delayedActionService, never()).scheduleTurnOffLight(actionKey(), LIGHT_ENTITY_ID, Duration.ofSeconds(300));
        verifyNoInteractions(lightBrightnessService);
    }

    @Test
    void activeManualLeaseIsExposedForDashboard() {
        assertNull(automation.getActiveManualLease());

        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("off"),
            lightState("on")
        ));

        BathroomOccupancyAutomation.BathroomManualLease lease = automation.getActiveManualLease();
        assertNotNull(lease);
        assertEquals(LIGHT_ENTITY_ID, lease.entityId());
    }

    @Test
    void clearManualLeaseClearsOnlyConfiguredLight() {
        automation.handleBathroomLightStateChanged(lightStateChangedEvent(
            lightState("off"),
            lightState("on")
        ));
        assertNotNull(automation.getActiveManualLease());

        assertFalse(automation.clearManualLease("light.some_other_light"));
        assertNotNull(automation.getActiveManualLease());

        assertTrue(automation.clearManualLease(LIGHT_ENTITY_ID));
        assertNull(automation.getActiveManualLease());
    }

    private String actionKey() {
        return "automation:bathroom-occupancy:" + SENSOR_ENTITY_ID + ":" + LIGHT_ENTITY_ID + ":turn_off";
    }

    private HaWsStateChangedEvent<LightEntity> lightStateChangedEvent(LightEntity oldState, LightEntity newState) {
        return new HaWsStateChangedEvent<>(
            1,
            "event",
            new HaWsStateChangedEventPayload<>(
                "state_changed",
                new HaWsStateChangedData<>(LIGHT_ENTITY_ID, oldState, newState),
                "LOCAL",
                null
            )
        );
    }

    private LightEntity lightState(String state) {
        return new LightEntity(LIGHT_ENTITY_ID, state, null, null, null, null);
    }

    private LightEntity lightState(String state, String contextId) {
        return new LightEntity(LIGHT_ENTITY_ID, state, null, new LightEntity.Context(contextId, null, null), null, null);
    }
}
