package com.learn.weather_service.client;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.learn.weather_service.client.OpenMeteoModels.ForecastApiResponse;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingResponse;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingMatch;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingResult;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingSearchResponse;
import com.learn.weather_service.config.WeatherApiProperties;
import com.learn.weather_service.exception.CityNotFoundException;
import com.learn.weather_service.exception.UpstreamServiceException;
import com.learn.weather_service.model.Units;

@Component
public class OpenMeteoClient {

	private final RestClient restClient;
	private final WeatherApiProperties properties;

	public OpenMeteoClient(RestClient.Builder builder, WeatherApiProperties properties) {
		this.restClient = builder.build();
		this.properties = properties;
	}

	public GeocodingResult geocode(String city) {
		GeocodingResponse response;
		try {
			response = restClient.get()
					.uri(properties.geocodingUrl() + "?name={city}&count=1", city)
					.retrieve()
					.body(GeocodingResponse.class);
		}
		catch (RestClientException ex) {
			throw new UpstreamServiceException("Geocoding service call failed", ex);
		}
		if (response == null || response.results() == null || response.results().isEmpty()) {
			throw new CityNotFoundException(city);
		}
		return response.results().get(0);
	}

	public List<GeocodingMatch> searchCities(String query, int limit) {
		GeocodingSearchResponse response;
		try {
			response = restClient.get()
					.uri(properties.geocodingUrl() + "?name={q}&count={n}", query, limit)
					.retrieve()
					.body(GeocodingSearchResponse.class);
		}
		catch (RestClientException ex) {
			throw new UpstreamServiceException("Geocoding service call failed", ex);
		}
		if (response == null || response.results() == null) {
			return List.of();
		}
		return response.results();
	}

	public ForecastApiResponse fetchDaily(double latitude, double longitude, int days, Units units) {
		ForecastApiResponse response;
		try {
			response = restClient.get()
					.uri(properties.forecastUrl()
							+ "?latitude={lat}&longitude={lon}"
							+ "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum"
							+ "&forecast_days={days}&timezone=auto"
							+ "&temperature_unit={tu}&precipitation_unit={pu}",
							latitude, longitude, days, units.temperatureParam(), units.precipitationParam())
					.retrieve()
					.body(ForecastApiResponse.class);
		}
		catch (RestClientException ex) {
			throw new UpstreamServiceException("Forecast service call failed", ex);
		}
		if (response == null || response.daily() == null) {
			throw new UpstreamServiceException("Forecast service returned no data");
		}
		return response;
	}
}
