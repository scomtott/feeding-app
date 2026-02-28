package com.example.springboot.clients;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.example.springboot.models.home_assistant.LightEntity;
import com.example.springboot.properties.HomeAssistantProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class HomeAssistantHttpClient {

	private final HomeAssistantProperties homeAssistantProperties;
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	public HomeAssistantHttpClient(HomeAssistantProperties homeAssistantProperties, ObjectMapper objectMapper) {
		this.homeAssistantProperties = homeAssistantProperties;
		this.httpClient = HttpClient.newHttpClient();
		this.objectMapper = objectMapper;
	}

	private String get(String endpointPath) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(buildUri(endpointPath))
			.header("Authorization", "Bearer " + homeAssistantProperties.getApiKey())
			.header("Accept", "application/json")
			.GET()
			.build();

		return send(request, null);
	}

	private String post(String endpointPath, String jsonBody) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(buildUri(endpointPath))
			.header("Authorization", "Bearer " + homeAssistantProperties.getApiKey())
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
			.build();

		return send(request, jsonBody);
	}

	public List<LightEntity> getLightEntities() {
		String response = get("/api/states");
		try {
			List<LightEntity> entities = objectMapper.readValue(response, new TypeReference<List<LightEntity>>() {
			});
			return entities.stream()
				.filter(entity -> entity.entityId() != null && entity.entityId().startsWith("light."))
				.toList();
		} catch (RuntimeException e) {
			throw new IllegalStateException("Failed to deserialize Home Assistant light entities", e);
		}
	}

	public LightEntity getLightEntity(String entityId) {
		String normalizedEntityId = normalizeLightEntityId(entityId);
		String response = get("/api/states/" + normalizedEntityId);
		try {
			return objectMapper.readValue(response, LightEntity.class);
		} catch (RuntimeException e) {
			throw new IllegalStateException("Failed to deserialize Home Assistant light entity: " + normalizedEntityId, e);
		}
	}

	public void turnOnLight(String entityId) {
		postLightServiceAction("turn_on", entityId);
	}

	public void turnOffLight(String entityId) {
		postLightServiceAction("turn_off", entityId);
	}

    public void setBrightness(String entityId, int brightness) {
        String normalizedEntityId = normalizeLightEntityId(entityId);
		int normalizedBrightness = Math.max(0, Math.min(255, brightness));
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "entity_id", normalizedEntityId,
                "brightness", normalizedBrightness
            ));
            post("/api/services/light/turn_on", payload);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to serialize Home Assistant request payload for setting brightness", e);
        }
    }

	private void postLightServiceAction(String action, String entityId) {
		String normalizedEntityId = normalizeLightEntityId(entityId);
		try {
			String payload = objectMapper.writeValueAsString(Map.of("entity_id", normalizedEntityId));
			post("/api/services/light/" + action, payload);
		} catch (RuntimeException e) {
			throw new IllegalStateException("Failed to serialize Home Assistant request payload", e);
		}
	}

	private String normalizeLightEntityId(String entityId) {
		return entityId.startsWith("light.") ? entityId : "light." + entityId;
	}

	private URI buildUri(String endpointPath) {
		String baseUrl = homeAssistantProperties.getBaseUrl();
		String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		String normalizedPath = endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
		return URI.create(normalizedBase + normalizedPath);
	}

	private String send(HttpRequest request, String requestBody) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 400) {
				log.error(
					"Home Assistant API request failed: method={}, uri={}, status={}, requestBody={}, responseBody={}",
					request.method(),
					request.uri(),
					response.statusCode(),
					requestBody == null ? "<none>" : requestBody,
					response.body()
				);
				throw new IllegalStateException("Home Assistant API request failed with status " + response.statusCode() + ": " + response.body());
			}
			return response.body();
		} catch (IOException e) {
			throw new IllegalStateException("Home Assistant API request failed", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Home Assistant API request interrupted", e);
		}
	}
}
