package com.learn.weather_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.learn.weather_service.client.OpenMeteoModels.ForecastApiResponse;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingResponse;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingResult;
import com.learn.weather_service.config.WeatherApiProperties;
import com.learn.weather_service.exception.CityNotFoundException;
import com.learn.weather_service.exception.UpstreamServiceException;

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

	public ForecastApiResponse fetchDaily(double latitude, double longitude, int days) {
		ForecastApiResponse response;
		try {
			response = restClient.get()
					.uri(properties.forecastUrl()
							+ "?latitude={lat}&longitude={lon}"
							+ "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum"
							+ "&forecast_days={days}&timezone=auto",
							latitude, longitude, days)
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
