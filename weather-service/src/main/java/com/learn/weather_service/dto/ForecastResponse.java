package com.learn.weather_service.dto;

import java.util.List;

public record ForecastResponse(String city, String country, double latitude, double longitude,
		List<DailyForecast> days) {
}
