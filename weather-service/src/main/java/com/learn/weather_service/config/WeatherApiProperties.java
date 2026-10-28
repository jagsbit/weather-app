package com.learn.weather_service.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("weather.api")
public record WeatherApiProperties(String geocodingUrl, String forecastUrl, Duration connectTimeout,
		Duration readTimeout) {
}
