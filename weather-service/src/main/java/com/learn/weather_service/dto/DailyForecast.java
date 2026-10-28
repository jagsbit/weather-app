package com.learn.weather_service.dto;

public record DailyForecast(String date, double minTempC, double maxTempC, double precipitationMm, int weatherCode,
		String summary) {
}
