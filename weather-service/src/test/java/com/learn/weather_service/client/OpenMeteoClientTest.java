package com.learn.weather_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.learn.weather_service.client.OpenMeteoModels.ForecastApiResponse;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingMatch;
import com.learn.weather_service.client.OpenMeteoModels.GeocodingResult;
import com.learn.weather_service.config.WeatherApiProperties;
import com.learn.weather_service.exception.CityNotFoundException;
import com.learn.weather_service.exception.UpstreamServiceException;
import com.learn.weather_service.model.Units;

class OpenMeteoClientTest {

	static final String GEO = "https://geo.test/v1/search";
	static final String FC = "https://fc.test/v1/forecast";

	MockRestServiceServer server;
	OpenMeteoClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new OpenMeteoClient(builder,
				new WeatherApiProperties(GEO, FC, Duration.ofSeconds(1), Duration.ofSeconds(1)));
	}

	@Test
	void geocodeReturnsFirstResultAndIgnoresUnknownFields() {
		String body = "{\"results\":["
				+ "{\"id\":1,\"name\":\"London\",\"country\":\"United Kingdom\",\"latitude\":51.5,"
				+ "\"longitude\":-0.12,\"extra\":\"x\"},"
				+ "{\"name\":\"London\",\"country\":\"Canada\",\"latitude\":42.9,\"longitude\":-81.2}],"
				+ "\"generationtime_ms\":0.5}";
		server.expect(requestTo(GEO + "?name=London&count=1")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		GeocodingResult r = client.geocode("London");

		assertThat(r.name()).isEqualTo("London");
		assertThat(r.country()).isEqualTo("United Kingdom");
		assertThat(r.latitude()).isEqualTo(51.5);
		assertThat(r.longitude()).isEqualTo(-0.12);
		server.verify();
	}

	@Test
	void geocodeEncodesCityName() {
		server.expect(requestTo(GEO + "?name=New%20York&count=1")).andRespond(withSuccess(
				"{\"results\":[{\"name\":\"New York\",\"country\":\"US\",\"latitude\":1,\"longitude\":2}]}",
				MediaType.APPLICATION_JSON));
		assertThat(client.geocode("New York").name()).isEqualTo("New York");
		server.verify();
	}

	@Test
	void geocodeMissingResultsKeyThrowsCityNotFound() {
		server.expect(requestTo(GEO + "?name=Zzz&count=1"))
				.andRespond(withSuccess("{\"generationtime_ms\":0.1}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.geocode("Zzz")).isInstanceOf(CityNotFoundException.class)
				.hasMessage("City not found: Zzz");
	}

	@Test
	void geocodeEmptyResultsThrowsCityNotFound() {
		server.expect(requestTo(GEO + "?name=Zzz&count=1"))
				.andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.geocode("Zzz")).isInstanceOf(CityNotFoundException.class);
	}

	@Test
	void geocodeServerErrorWrappedAsUpstream() {
		server.expect(requestTo(GEO + "?name=London&count=1")).andRespond(withServerError());
		assertThatThrownBy(() -> client.geocode("London")).isInstanceOf(UpstreamServiceException.class)
				.hasMessage("Geocoding service call failed").hasCauseInstanceOf(RestClientException.class);
	}

	@Test
	void geocodeClientErrorWrappedAsUpstream() {
		server.expect(requestTo(GEO + "?name=London&count=1")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		assertThatThrownBy(() -> client.geocode("London")).isInstanceOf(UpstreamServiceException.class);
	}

	@Test
	void fetchDailyParsesResponseAndSendsExpectedQuery() {
		String body = "{\"latitude\":51.5,\"daily\":{\"time\":[\"2026-10-30\",\"2026-10-31\"],"
				+ "\"weather_code\":[3,61],\"temperature_2m_max\":[14.2,13.0],"
				+ "\"temperature_2m_min\":[8.1,7.5],\"precipitation_sum\":[0.4,2.0]}}";
		server.expect(method(HttpMethod.GET)).andExpect(queryParam("latitude", "51.5"))
				.andExpect(queryParam("longitude", "-0.12"))
				.andExpect(queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum"))
				.andExpect(queryParam("temperature_unit", "celsius"))
				.andExpect(queryParam("precipitation_unit", "mm")).andExpect(queryParam("forecast_days", "5")).andExpect(queryParam("timezone", "auto"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		ForecastApiResponse r = client.fetchDaily(51.5, -0.12, 5, Units.METRIC);

		assertThat(r.daily().time()).containsExactly("2026-10-30", "2026-10-31");
		assertThat(r.daily().weatherCode()).containsExactly(3, 61);
		assertThat(r.daily().temperatureMax()).containsExactly(14.2, 13.0);
		assertThat(r.daily().temperatureMin()).containsExactly(8.1, 7.5);
		assertThat(r.daily().precipitationSum()).containsExactly(0.4, 2.0);
		server.verify();
	}

	@Test
	void fetchDailyImperialSendsFahrenheitAndInch() {
		String body = "{\"daily\":{\"time\":[\"2026-10-30\"],\"weather_code\":[3],"
				+ "\"temperature_2m_max\":[57.6],\"temperature_2m_min\":[46.5],\"precipitation_sum\":[0.02]}}";
		server.expect(method(HttpMethod.GET)).andExpect(queryParam("temperature_unit", "fahrenheit"))
				.andExpect(queryParam("precipitation_unit", "inch"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		ForecastApiResponse r = client.fetchDaily(51.5, -0.12, 5, Units.IMPERIAL);

		assertThat(r.daily().temperatureMax()).containsExactly(57.6);
		assertThat(r.daily().precipitationSum()).containsExactly(0.02);
		server.verify();
	}

	@Test
	void fetchDailyServerErrorWrappedAsUpstream() {
		server.expect(method(HttpMethod.GET)).andRespond(withServerError());
		assertThatThrownBy(() -> client.fetchDaily(1, 2, 5, Units.METRIC)).isInstanceOf(UpstreamServiceException.class)
				.hasMessage("Forecast service call failed");
	}

	@Test
	void fetchDailyMissingDailyThrowsUpstream() {
		server.expect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"latitude\":1}", MediaType.APPLICATION_JSON));
		assertThatThrownBy(() -> client.fetchDaily(1, 2, 5, Units.METRIC)).isInstanceOf(UpstreamServiceException.class)
				.hasMessage("Forecast service returned no data");
	}

	@Test
	void searchCitiesParsesMultipleResultsAndMapsFields() {
		String body = "{\"results\":["
				+ "{\"id\":1,\"name\":\"London\",\"admin1\":\"England\",\"country\":\"United Kingdom\","
				+ "\"country_code\":\"GB\",\"latitude\":51.5,\"longitude\":-0.12,\"extra\":\"x\"},"
				+ "{\"id\":2,\"name\":\"London\",\"admin1\":\"Ontario\",\"country\":\"Canada\","
				+ "\"country_code\":\"CA\",\"latitude\":42.9,\"longitude\":-81.2}],"
				+ "\"generationtime_ms\":0.5}";
		server.expect(requestTo(GEO + "?name=Lon&count=5")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

		List<GeocodingMatch> r = client.searchCities("Lon", 5);

		assertThat(r).hasSize(2);
		assertThat(r.get(0).id()).isEqualTo(1L);
		assertThat(r.get(0).name()).isEqualTo("London");
		assertThat(r.get(0).admin1()).isEqualTo("England");
		assertThat(r.get(0).country()).isEqualTo("United Kingdom");
		assertThat(r.get(0).countryCode()).isEqualTo("GB");
		assertThat(r.get(0).latitude()).isEqualTo(51.5);
		assertThat(r.get(0).longitude()).isEqualTo(-0.12);
		assertThat(r.get(1).countryCode()).isEqualTo("CA");
		server.verify();
	}

	@Test
	void searchCitiesEncodesSpaces() {
		server.expect(requestTo(GEO + "?name=New%20York&count=3"))
				.andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

		assertThat(client.searchCities("New York", 3)).isEmpty();
		server.verify();
	}

	@Test
	void searchCitiesMissingResultsKeyReturnsEmptyList() {
		server.expect(requestTo(GEO + "?name=Zzzz&count=5"))
				.andRespond(withSuccess("{\"generationtime_ms\":0.1}", MediaType.APPLICATION_JSON));

		assertThat(client.searchCities("Zzzz", 5)).isEmpty();
	}

	@Test
	void searchCitiesEmptyObjectBodyReturnsEmptyList() {
		server.expect(requestTo(GEO + "?name=Zzzz&count=5"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThat(client.searchCities("Zzzz", 5)).isEmpty();
	}

	@Test
	void searchCitiesServerErrorThrowsUpstreamServiceException() {
		server.expect(requestTo(GEO + "?name=Lon&count=5")).andRespond(withServerError());

		assertThatThrownBy(() -> client.searchCities("Lon", 5)).isInstanceOf(UpstreamServiceException.class)
				.hasMessage("Geocoding service call failed");
	}
}
