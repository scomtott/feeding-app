package com.example.springboot.homeassistant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.springboot.homeassistant.automations.BathroomOccupancyAutomation;
import com.example.springboot.homeassistant.events.BinarySensorStateChangedEvent;
import com.example.springboot.homeassistant.events.LightStateChangedEvent;
import com.example.springboot.homeassistant.events.StateChangedEventRouter;
import com.example.springboot.homeassistant.events.StateChangedSubscriptionRegistry;
import com.example.springboot.homeassistant.services.BinarySensorService;
import com.example.springboot.homeassistant.services.LightBrightnessService;
import com.example.springboot.homeassistant.services.LightStateChangeLoggingService;

/**
 * Wires every subscriber to the Home Assistant {@code state_changed} event stream.
 *
 * <p>Subscriptions are a compile-time concern: a change to the set is a code change and a redeploy.
 * There is no runtime registration, no discovery, and no plugin surface.
 */
@Configuration
public class HomeAssistantEventSubscriptions {

    private static final int ORDER_LIGHT_BRIGHTNESS = 10;
    private static final int ORDER_BATHROOM_OCCUPANCY = 20;
    private static final int ORDER_LIGHT_LOGGING = 30;
    private static final int ORDER_BINARY_SENSOR = 10;

    /**
     * The {@code order} values reproduce the exact sequence the websocket handler used to call these
     * services in, so ordering cannot drift with classpath or scan order.
     *
     * <p>The lambdas must close over these <strong>injected</strong> parameters. Constructing a
     * service directly, or binding to a raw instance rather than the bean, would bypass Spring's
     * {@code @Async} proxy and silently move that handler off {@code telemetry-*} onto
     * {@code ha-event-*} — with no test failure, because the observable behaviour is identical.
     */
    @Bean
    StateChangedEventRouter stateChangedEventRouter(
        LightBrightnessService brightnessService,
        BathroomOccupancyAutomation bathroomOccupancyAutomation,
        LightStateChangeLoggingService lightStateChangeLoggingService,
        BinarySensorService binarySensorService
    ) {
        return new StateChangedEventRouter(new StateChangedSubscriptionRegistry()
            .subscribe(LightStateChangedEvent.class, event -> true,
                event -> brightnessService.handleLightStateChanged(event),
                ORDER_LIGHT_BRIGHTNESS, "light-brightness")
            .subscribe(LightStateChangedEvent.class, event -> true,
                event -> bathroomOccupancyAutomation.handleBathroomLightStateChanged(event),
                ORDER_BATHROOM_OCCUPANCY, "bathroom-occupancy")
            .subscribe(LightStateChangedEvent.class, event -> true,
                event -> lightStateChangeLoggingService.handleLightStateChanged(event),
                ORDER_LIGHT_LOGGING, "light-logging")
            .subscribe(BinarySensorStateChangedEvent.class, event -> true,
                event -> binarySensorService.handleBinarySensorStateChanged(event),
                ORDER_BINARY_SENSOR, "binary-sensor")
            .build());
    }
}
