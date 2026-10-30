package com.learn.weather_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.learn.weather_service.client.OpenMeteoClient;
import com.learn.weather_service.client.OpenMeteoModels.Daily;
import com.learn.weather_service.client.OpenMeteoModels.ForecastApiResponse;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingResult;
import com.learn.weather_service.dto.ForecastResponse;
import com.learn.weather_service.exception.CityNotFoundException;
import com.learn.weather_service.exception.UpstreamServiceException;
import com.learn.weather_service.model.Units;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

	private static final List<String> T5 = List.of("d1", "d2", "d3", "d4", "d5");
	private static final List<Double> D5 = List.of(1.0, 1.0, 1.0, 1.0, 1.0);
	private static final List<Integer> C5 = List.of(0, 0, 0, 0, 0);

	@Mock
	OpenMeteoClient client;

	ForecastService service;

	final GeocodingResult london = new GeocodingResult("London", "United Kingdom", 51.5, -0.12);

	@BeforeEach
	void setUp() {
		service = new ForecastService(client);
	}

	private Daily daily(List<String> time, List<Integer> codes, List<Double> max, List<Double> min,
			List<Double> precip) {
		return new Daily(time, codes, max, min, precip);
	}

	private Daily validDaily(List<Integer> codes) {
		return daily(T5, codes, List.of(10.0, 11.0, 12.0, 13.0, 14.0), List.of(1.0, 2.0, 3.0, 4.0, 5.0),
				List.of(0.0, 0.1, 0.2, 0.3, 0.4));
	}

	@Test
	void happyPathMapsFiveDays() {
		when(client.geocode("London")).thenReturn(london);
		when(client.fetchDaily(51.5, -0.12, 5, Units.METRIC))
				.thenReturn(new ForecastApiResponse(validDaily(List.of(3, 0, 61, 95, 99))));

		ForecastResponse r = service.getForecast("London", Units.METRIC);

		assertThat(r.city()).isEqualTo("London");
		assertThat(r.country()).isEqualTo("United Kingdom");
		assertThat(r.latitude()).isEqualTo(51.5);
		assertThat(r.longitude()).isEqualTo(-0.12);
		assertThat(r.days()).hasSize(5);
		assertThat(r.days().get(0).date()).isEqualTo("d1");
		assertThat(r.days().get(0).minTemp()).isEqualTo(1.0);
		assertThat(r.days().get(0).maxTemp()).isEqualTo(10.0);
		assertThat(r.days().get(0).precipitation()).isEqualTo(0.0);
		assertThat(r.days().get(0).weatherCode()).isEqualTo(3);
		assertThat(r.days().get(0).summary()).isEqualTo("Overcast");
		assertThat(r.days().get(1).summary()).isEqualTo("Clear sky");
		assertThat(r.days().get(2).summary()).isEqualTo("Slight rain");
		assertThat(r.days().get(3).summary()).isEqualTo("Thunderstorm");
		assertThat(r.days().get(4).summary()).isEqualTo("Thunderstorm with heavy hail");
		assertThat(r.days().get(4).maxTemp()).isEqualTo(14.0);
		assertThat(r.days().get(4).minTemp()).isEqualTo(5.0);
		assertThat(r.days().get(4).precipitation()).isEqualTo(0.4);
	}

	@Test
	void metricReportsMetricUnits() {
		when(client.geocode("London")).thenReturn(london);
		when(client.fetchDaily(51.5, -0.12, 5, Units.METRIC))
				.thenReturn(new ForecastApiResponse(validDaily(List.of(0, 0, 0, 0, 0))));
		ForecastResponse r = service.getForecast("London", Units.METRIC);
		assertThat(r.units()).isEqualTo("metric");
		assertThat(r.temperatureUnit()).isEqualTo("°C");
		assertThat(r.precipitationUnit()).isEqualTo("mm");
	}

	@Test
	void imperialPassesUnitsToClientAndValuesThroughUnconverted() {
		when(client.geocode("London")).thenReturn(london);
		List<Double> fifty = List.of(50.0, 50.0, 50.0, 50.0, 50.0);
		when(client.fetchDaily(51.5, -0.12, 5, Units.IMPERIAL))
				.thenReturn(new ForecastApiResponse(daily(T5, C5, fifty, fifty, fifty)));

		ForecastResponse r = service.getForecast("London", Units.IMPERIAL);

		verify(client).fetchDaily(51.5, -0.12, 5, Units.IMPERIAL);
		assertThat(r.units()).isEqualTo("imperial");
		assertThat(r.temperatureUnit()).isEqualTo("°F");
		assertThat(r.precipitationUnit()).isEqualTo("in");
		assertThat(r.days().get(0).minTemp()).isEqualTo(50.0);
		assertThat(r.days().get(0).maxTemp()).isEqualTo(50.0);
		assertThat(r.days().get(0).precipitation()).isEqualTo(50.0);
	}

	@Test
	void unknownWeatherCodeGivesUnknownSummary() {
		when(client.geocode("London")).thenReturn(london);
		when(client.fetchDaily(51.5, -0.12, 5, Units.METRIC))
				.thenReturn(new ForecastApiResponse(validDaily(List.of(4, 4, 4, 4, 1234))));

		ForecastResponse r = service.getForecast("London", Units.METRIC);

		assertThat(r.days()).allSatisfy(d -> assertThat(d.summary()).isEqualTo("Unknown"));
	}

	@Test
	void cityNotFoundPropagatesAndSkipsForecastCall() {
		when(client.geocode("Nowhere")).thenThrow(new CityNotFoundException("Nowhere"));

		assertThatThrownBy(() -> service.getForecast("Nowhere", Units.METRIC)).isInstanceOf(CityNotFoundException.class);
		verify(client).geocode("Nowhere");
		verifyNoMoreInteractions(client);
	}

	@Test
	void upstreamFailureFromForecastPropagates() {
		when(client.geocode("London")).thenReturn(london);
		when(client.fetchDaily(51.5, -0.12, 5, Units.METRIC)).thenThrow(new UpstreamServiceException("boom"));

		assertThatThrownBy(() -> service.getForecast("London", Units.METRIC)).isInstanceOf(UpstreamServiceException.class)
				.hasMessage("boom");
	}

	private void assertMalformed(Daily d) {
		when(client.geocode("London")).thenReturn(london);
		when(client.fetchDaily(51.5, -0.12, 5, Units.METRIC)).thenReturn(new ForecastApiResponse(d));
		assertThatThrownBy(() -> service.getForecast("London", Units.METRIC)).isInstanceOf(UpstreamServiceException.class)
				.hasMessage("Forecast service returned malformed daily data");
	}

	@Test
	void shortTimeListIsMalformed() {
		assertMalformed(daily(List.of("d1", "d2"), C5, D5, D5, D5));
	}

	@Test
	void tooManyWeatherCodesIsMalformed() {
		assertMalformed(daily(T5, List.of(0, 0, 0, 0, 0, 0), D5, D5, D5));
	}

	@Test
	void shortMinListIsMalformed() {
		assertMalformed(daily(T5, C5, D5, List.of(1.0), D5));
	}

	@Test
	void shortMaxListIsMalformed() {
		assertMalformed(daily(T5, C5, List.of(1.0), D5, D5));
	}

	@Test
	void shortPrecipListIsMalformed() {
		assertMalformed(daily(T5, C5, D5, D5, List.of(1.0)));
	}

	@Test
	void allNullListsAreMalformed() {
		assertMalformed(daily(null, null, null, null, null));
	}

	@Test
	void nullWeatherCodeListIsMalformed() {
		assertMalformed(daily(T5, null, D5, D5, D5));
	}

	@Test
	void nullPrecipListIsMalformed() {
		assertMalformed(daily(T5, C5, D5, D5, null));
	}
}
