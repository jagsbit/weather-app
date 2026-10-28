package com.learn.weather_service.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class OpenMeteoModels {

	private OpenMeteoModels() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GeocodingResponse(List<GeocodingResult> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GeocodingResult(String name, String country, double latitude, double longitude) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ForecastApiResponse(Daily daily) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Daily(List<String> time,
			@JsonProperty("weather_code") List<Integer> weatherCode,
			@JsonProperty("temperature_2m_max") List<Double> temperatureMax,
			@JsonProperty("temperature_2m_min") List<Double> temperatureMin,
			@JsonProperty("precipitation_sum") List<Double> precipitationSum) {
	}
}
