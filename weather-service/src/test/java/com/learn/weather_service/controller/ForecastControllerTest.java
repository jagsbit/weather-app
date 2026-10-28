package com.learn.weather_service.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.learn.weather_service.dto.DailyForecast;
import com.learn.weather_service.dto.ForecastResponse;
import com.learn.weather_service.exception.CityNotFoundException;
import com.learn.weather_service.exception.UpstreamServiceException;
import com.learn.weather_service.service.ForecastService;

@WebMvcTest(ForecastController.class)
class ForecastControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ForecastService service;

	@Test
	void okReturnsForecastShape() throws Exception {
		List<DailyForecast> days = List.of(new DailyForecast("2026-10-30", 8.1, 14.2, 0.4, 3, "Overcast"),
				new DailyForecast("2026-10-31", 7.0, 12.0, 0.0, 0, "Clear sky"));
		when(service.getForecast("London"))
				.thenReturn(new ForecastResponse("London", "United Kingdom", 51.5, -0.12, days));

		mvc.perform(get("/api/forecast").param("city", "London")).andExpect(status().isOk())
				.andExpect(jsonPath("$.city").value("London"))
				.andExpect(jsonPath("$.country").value("United Kingdom"))
				.andExpect(jsonPath("$.latitude").value(51.5)).andExpect(jsonPath("$.longitude").value(-0.12))
				.andExpect(jsonPath("$.days.length()").value(2))
				.andExpect(jsonPath("$.days[0].date").value("2026-10-30"))
				.andExpect(jsonPath("$.days[0].minTempC").value(8.1))
				.andExpect(jsonPath("$.days[0].maxTempC").value(14.2))
				.andExpect(jsonPath("$.days[0].precipitationMm").value(0.4))
				.andExpect(jsonPath("$.days[0].weatherCode").value(3))
				.andExpect(jsonPath("$.days[0].summary").value("Overcast"));
	}

	@Test
	void cityIsTrimmed() throws Exception {
		when(service.getForecast("London")).thenReturn(new ForecastResponse("London", "UK", 1, 2, List.of()));
		mvc.perform(get("/api/forecast").param("city", "  London  ")).andExpect(status().isOk());
		verify(service).getForecast("London");
	}

	@Test
	void missingCityIs400() throws Exception {
		mvc.perform(get("/api/forecast")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Query parameter 'city' is required"));
		verifyNoInteractions(service);
	}

	@Test
	void emptyCityIs400() throws Exception {
		mvc.perform(get("/api/forecast").param("city", "")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"));
		verifyNoInteractions(service);
	}

	@Test
	void blankCityIs400() throws Exception {
		mvc.perform(get("/api/forecast").param("city", "   ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"));
		verifyNoInteractions(service);
	}

	@Test
	void cityNotFoundIs404() throws Exception {
		when(service.getForecast("Nowhere")).thenThrow(new CityNotFoundException("Nowhere"));
		mvc.perform(get("/api/forecast").param("city", "Nowhere")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("City not found: Nowhere"));
	}

	@Test
	void upstreamFailureIs502() throws Exception {
		when(service.getForecast("London")).thenThrow(new UpstreamServiceException("down"));
		mvc.perform(get("/api/forecast").param("city", "London")).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("Bad Gateway"))
				.andExpect(jsonPath("$.message").value("down"));
	}
}
