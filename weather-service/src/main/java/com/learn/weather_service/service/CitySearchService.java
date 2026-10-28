package com.learn.weather_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.learn.weather_service.client.OpenMeteoClient;
import com.learn.weather_service.dto.CitySearchResponse;
import com.learn.weather_service.dto.CitySearchResult;

@Service
public class CitySearchService {

	public static final int MAX_LIMIT = 10;

	private final OpenMeteoClient client;

	public CitySearchService(OpenMeteoClient client) {
		this.client = client;
	}

	public CitySearchResponse search(String query, int limit) {
		List<CitySearchResult> results = client.searchCities(query, limit).stream()
				.map(m -> new CitySearchResult(m.name(), m.admin1(), m.country(), m.countryCode(), m.latitude(),
						m.longitude()))
				.toList();
		return new CitySearchResponse(query, results);
	}
}
