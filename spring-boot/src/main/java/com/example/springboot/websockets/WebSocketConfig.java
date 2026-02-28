package com.example.springboot.websockets;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import com.example.springboot.properties.HomeAssistantProperties;

@Configuration
public class WebSocketConfig {

    private final HomeAssistantProperties homeAssistantProperties;

    public WebSocketConfig(HomeAssistantProperties homeAssistantProperties) {
        this.homeAssistantProperties = homeAssistantProperties;
    }

    @Bean
    public WebSocketConnectionManager homeAssistantWebSocketConnectionManager(HomeAssistantWebSocketHandler homeAssistantWebSocketHandler) {
        String websocketUrl = toHomeAssistantWebSocketUrl(homeAssistantProperties.getBaseUrl());
        WebSocketConnectionManager manager = new WebSocketConnectionManager(new StandardWebSocketClient(), homeAssistantWebSocketHandler, websocketUrl);
        manager.setAutoStartup(true);
        return manager;
    }

    private String toHomeAssistantWebSocketUrl(String baseUrl) {
        URI uri = URI.create(baseUrl);
        String scheme = "https".equalsIgnoreCase(uri.getScheme()) ? "wss" : "ws";

        StringBuilder builder = new StringBuilder();
        builder.append(scheme).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            builder.append(":").append(uri.getPort());
        }
        builder.append("/api/websocket");

        return builder.toString();
    }
}