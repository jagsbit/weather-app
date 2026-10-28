package com.learn.weather_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.learn.weather_service.dto.ForecastResponse;
import com.learn.weather_service.exception.InvalidRequestException;
import com.learn.weather_service.service.ForecastService;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {

	private final ForecastService service;

	public ForecastController(ForecastService service) {
		this.service = service;
	}

	@GetMapping
	public ForecastResponse getForecast(@RequestParam(required = false) String city) {
		if (city == null || city.isBlank()) {
			throw new InvalidRequestException("Query parameter 'city' is required");
		}
		return service.getForecast(city.trim());
	}
}
