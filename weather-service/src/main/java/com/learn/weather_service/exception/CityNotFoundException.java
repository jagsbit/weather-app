package com.learn.weather_service.exception;

public class CityNotFoundException extends RuntimeException {

	public CityNotFoundException(String city) {
		super("City not found: " + city);
	}
}
