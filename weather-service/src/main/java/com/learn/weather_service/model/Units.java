package com.learn.weather_service.model;

import com.learn.weather_service.exception.InvalidRequestException;

public enum Units {

	METRIC("celsius", "mm", "°C", "mm"),
	IMPERIAL("fahrenheit", "inch", "°F", "in");

	private final String temperatureParam;
	private final String precipitationParam;
	private final String temperatureLabel;
	private final String precipitationLabel;

	Units(String temperatureParam, String precipitationParam, String temperatureLabel, String precipitationLabel) {
		this.temperatureParam = temperatureParam;
		this.precipitationParam = precipitationParam;
		this.temperatureLabel = temperatureLabel;
		this.precipitationLabel = precipitationLabel;
	}

	public String temperatureParam() {
		return temperatureParam;
	}

	public String precipitationParam() {
		return precipitationParam;
	}

	public String temperatureLabel() {
		return temperatureLabel;
	}

	public String precipitationLabel() {
		return precipitationLabel;
	}

	/** Lower-case name used in the API ("metric" or "imperial"). */
	public String id() {
		return name().toLowerCase();
	}

	/** Null means the default (metric); anything else, including empty, must match. */
	public static Units parse(String value) {
		if (value == null) {
			return METRIC;
		}
		String v = value.trim();
		if (v.equalsIgnoreCase("metric")) {
			return METRIC;
		}
		if (v.equalsIgnoreCase("imperial")) {
			return IMPERIAL;
		}
		throw new InvalidRequestException("Query parameter 'units' must be 'metric' or 'imperial'");
	}
}
