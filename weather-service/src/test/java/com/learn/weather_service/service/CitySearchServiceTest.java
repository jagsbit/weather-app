package com.learn.weather_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.learn.weather_service.client.OpenMeteoClient;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingMatch;
import com.learn.weather_service.dto.CitySearchResponse;
import com.learn.weather_service.exception.UpstreamServiceException;

@ExtendWith(MockitoExtension.class)
class CitySearchServiceTest {

	@Mock
	OpenMeteoClient client;

	CitySearchService service;

	@BeforeEach
	void setUp() {
		service = new CitySearchService(client);
	}

	@Test
	void mapsMatchesToResults() {
		when(client.searchCities("lon", 3)).thenReturn(List.of(
				new GeocodingMatch(1, "London", "England", "United Kingdom", "GB", 51.5, -0.12),
				new GeocodingMatch(2, "London", "Ontario", "Canada", "CA", 42.9, -81.2)));

		CitySearchResponse r = service.search("lon", 3);

		assertThat(r.query()).isEqualTo("lon");
		assertThat(r.results()).hasSize(2);
		assertThat(r.results().get(0).name()).isEqualTo("London");
		assertThat(r.results().get(0).region()).isEqualTo("England");
		assertThat(r.results().get(0).country()).isEqualTo("United Kingdom");
		assertThat(r.results().get(0).countryCode()).isEqualTo("GB");
		assertThat(r.results().get(0).latitude()).isEqualTo(51.5);
		assertThat(r.results().get(0).longitude()).isEqualTo(-0.12);
		assertThat(r.results().get(1).region()).isEqualTo("Ontario");
	}

	@Test
	void nullAdmin1BecomesNullRegion() {
		when(client.searchCities("sin", 5))
				.thenReturn(List.of(new GeocodingMatch(3, "Singapore", null, "Singapore", "SG", 1.3, 103.8)));

		assertThat(service.search("sin", 5).results().get(0).region()).isNull();
	}

	@Test
	void emptyClientListGivesEmptyResults() {
		when(client.searchCities("zz", 5)).thenReturn(List.of());

		CitySearchResponse r = service.search("zz", 5);

		assertThat(r.query()).isEqualTo("zz");
		assertThat(r.results()).isEmpty();
	}

	@Test
	void upstreamExceptionPropagates() {
		when(client.searchCities("lon", 5)).thenThrow(new UpstreamServiceException("boom"));

		assertThatThrownBy(() -> service.search("lon", 5)).isInstanceOf(UpstreamServiceException.class);
	}

	@Test
	void limitPassedUnchanged() {
		when(client.searchCities("lon", 50)).thenReturn(List.of());

		service.search("lon", 50);

		verify(client).searchCities("lon", 50);
	}
}
