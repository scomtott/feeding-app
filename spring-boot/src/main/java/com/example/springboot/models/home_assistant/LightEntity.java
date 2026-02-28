package com.example.springboot.models.home_assistant;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LightEntity(
	@JsonProperty("entity_id") String entityId,
	String state,
	Attributes attributes,
	@JsonProperty("last_changed") String lastChanged,
	@JsonProperty("last_updated") String lastUpdated
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Attributes(
		@JsonProperty("friendly_name") String friendlyName,
		Integer brightness,
		@JsonProperty("color_mode") ColorMode colorMode,
		@JsonProperty("supported_color_modes") Set<ColorMode> supportedColorModes,
		@JsonProperty("color_temp_kelvin") Integer colorTempKelvin,
		@JsonProperty("min_color_temp_kelvin") Integer minColorTempKelvin,
		@JsonProperty("max_color_temp_kelvin") Integer maxColorTempKelvin,
		@JsonProperty("hs_color") List<Double> hsColor,
		@JsonProperty("rgb_color") List<Integer> rgbColor,
		@JsonProperty("rgbw_color") List<Integer> rgbwColor,
		@JsonProperty("rgbww_color") List<Integer> rgbwwColor,
		@JsonProperty("xy_color") List<Double> xyColor,
		String effect,
		@JsonProperty("effect_list") List<String> effectList
	) {
	}

	public enum ColorMode {
		unknown,
		onoff,
		brightness,
		color_temp,
		hs,
		xy,
		rgb,
		rgbw,
		rgbww,
		white
	}
}
