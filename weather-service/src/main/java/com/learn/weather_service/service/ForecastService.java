package com.learn.weather_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.learn.weather_service.client.OpenMeteoClient;
import com.learn.weather_service.client.OpenMeteoModels.Daily;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingResult;
import com.learn.weather_service.dto.DailyForecast;
import com.learn.weather_service.dto.ForecastResponse;
import com.learn.weather_service.exception.UpstreamServiceException;
import com.learn.weather_service.model.Units;

@Service
public class ForecastService {

	static final int FORECAST_DAYS = 5;

	private static final Map<Integer, String> WMO_SUMMARIES = Map.ofEntries(
			Map.entry(0, "Clear sky"), Map.entry(1, "Mainly clear"), Map.entry(2, "Partly cloudy"),
			Map.entry(3, "Overcast"), Map.entry(45, "Fog"), Map.entry(48, "Rime fog"),
			Map.entry(51, "Light drizzle"), Map.entry(53, "Moderate drizzle"), Map.entry(55, "Dense drizzle"),
			Map.entry(61, "Slight rain"), Map.entry(63, "Moderate rain"), Map.entry(65, "Heavy rain"),
			Map.entry(71, "Slight snow"), Map.entry(73, "Moderate snow"), Map.entry(75, "Heavy snow"),
			Map.entry(80, "Slight rain showers"), Map.entry(81, "Moderate rain showers"),
			Map.entry(82, "Violent rain showers"), Map.entry(95, "Thunderstorm"),
			Map.entry(96, "Thunderstorm with slight hail"), Map.entry(99, "Thunderstorm with heavy hail"));

	private final OpenMeteoClient client;

	public ForecastService(OpenMeteoClient client) {
		this.client = client;
	}

	public ForecastResponse getForecast(String city, Units units) {
		GeocodingResult place = client.geocode(city);
		Daily daily = client.fetchDaily(place.latitude(), place.longitude(), FORECAST_DAYS, units).daily();

		List<String> time = daily.time();
		if (!hasSize(time, FORECAST_DAYS)
				|| !hasSize(daily.temperatureMin(), FORECAST_DAYS)
				|| !hasSize(daily.temperatureMax(), FORECAST_DAYS)
				|| !hasSize(daily.precipitationSum(), FORECAST_DAYS)
				|| !hasSize(daily.weatherCode(), FORECAST_DAYS)) {
			throw new UpstreamServiceException("Forecast service returned malformed daily data");
		}

		List<DailyForecast> days = new ArrayList<>(FORECAST_DAYS);
		for (int i = 0; i < FORECAST_DAYS; i++) {
			int code = daily.weatherCode().get(i);
			days.add(new DailyForecast(time.get(i), daily.temperatureMin().get(i), daily.temperatureMax().get(i),
					daily.precipitationSum().get(i), code, WMO_SUMMARIES.getOrDefault(code, "Unknown")));
		}
		return new ForecastResponse(place.name(), place.country(), place.latitude(), place.longitude(),
				units.id(), units.temperatureLabel(), units.precipitationLabel(), days);
	}

	private static boolean hasSize(List<?> list, int size) {
		return list != null && list.size() == size;
	}
}
