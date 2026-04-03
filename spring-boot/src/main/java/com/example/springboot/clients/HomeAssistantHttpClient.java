package com.example.springboot.clients;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.example.springboot.properties.HomeAssistantProperties;

@Component
@Slf4j
public class HomeAssistantHttpClient {

	private final HomeAssistantProperties homeAssistantProperties;
	private final HttpClient httpClient;

	public HomeAssistantHttpClient(HomeAssistantProperties homeAssistantProperties) {
		this.homeAssistantProperties = homeAssistantProperties;
		this.httpClient = HttpClient.newHttpClient();
	}

	public String get(String endpointPath) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(buildUri(endpointPath))
			.header("Authorization", "Bearer " + homeAssistantProperties.getApiKey())
			.header("Accept", "application/json")
			.GET()
			.build();

		return send(request, null);
	}

	public String post(String endpointPath, String jsonBody) {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(buildUri(endpointPath))
			.header("Authorization", "Bearer " + homeAssistantProperties.getApiKey())
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
			.build();

		return send(request, jsonBody);
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
