package com.learn.weather_service.dto;

public record CitySearchResult(String name, String region, String country, String countryCode, double latitude,
		double longitude) {
}
