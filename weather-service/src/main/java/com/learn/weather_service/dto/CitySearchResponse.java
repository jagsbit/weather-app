package com.learn.weather_service.dto;

import java.util.List;

public record CitySearchResponse(String query, List<CitySearchResult> results) {
}
