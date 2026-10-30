package com.learn.weather_service.dto;

public record DailyForecast(String date, double minTemp, double maxTemp, double precipitation, int weatherCode,
		String summary) {
}
