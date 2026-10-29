package com.learn.weather_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.learn.weather_service.dto.CitySearchResponse;
import com.learn.weather_service.exception.InvalidRequestException;
import com.learn.weather_service.service.CitySearchService;

@RestController
@RequestMapping("/api/cities")
public class CitySearchController {

	private static final int MIN_QUERY_LENGTH = 2;

	private final CitySearchService service;

	public CitySearchController(CitySearchService service) {
		this.service = service;
	}

	@GetMapping("/search")
	public CitySearchResponse search(@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "5") int limit) {
		if (query == null || query.isBlank()) {
			throw new InvalidRequestException("Query parameter 'query' is required");
		}
		String trimmed = query.trim();
		if (trimmed.length() < MIN_QUERY_LENGTH) {
			throw new InvalidRequestException("Query parameter 'query' must be at least 2 characters");
		}
		if (limit < 1 || limit > CitySearchService.MAX_LIMIT) {
			throw new InvalidRequestException("Query parameter 'limit' must be between 1 and 10");
		}
		return service.search(trimmed, limit);
	}
}
