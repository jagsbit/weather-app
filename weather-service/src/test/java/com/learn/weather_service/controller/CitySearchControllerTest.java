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

import com.learn.weather_service.dto.CitySearchResponse;
import com.learn.weather_service.dto.CitySearchResult;
import com.learn.weather_service.exception.UpstreamServiceException;
import com.learn.weather_service.service.CitySearchService;

@WebMvcTest(CitySearchController.class)
class CitySearchControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	CitySearchService service;

	@Test
	void okReturnsShape() throws Exception {
		when(service.search("lon", 3)).thenReturn(new CitySearchResponse("lon",
				List.of(new CitySearchResult("London", "England", "United Kingdom", "GB", 51.5, -0.12))));

		mvc.perform(get("/api/cities/search").param("query", "lon").param("limit", "3"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.query").value("lon"))
				.andExpect(jsonPath("$.results.length()").value(1))
				.andExpect(jsonPath("$.results[0].name").value("London"))
				.andExpect(jsonPath("$.results[0].region").value("England"))
				.andExpect(jsonPath("$.results[0].country").value("United Kingdom"))
				.andExpect(jsonPath("$.results[0].countryCode").value("GB"))
				.andExpect(jsonPath("$.results[0].latitude").value(51.5))
				.andExpect(jsonPath("$.results[0].longitude").value(-0.12));
	}

	@Test
	void defaultLimitIsFiveAndQueryTrimmed() throws Exception {
		when(service.search("lon", 5)).thenReturn(new CitySearchResponse("lon", List.of()));

		mvc.perform(get("/api/cities/search").param("query", "  lon  ")).andExpect(status().isOk());

		verify(service).search("lon", 5);
	}

	@Test
	void okWithEmptyResults() throws Exception {
		when(service.search("zz", 5)).thenReturn(new CitySearchResponse("zz", List.of()));

		mvc.perform(get("/api/cities/search").param("query", "zz")).andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(0));
	}

	@Test
	void boundaryLimitsAccepted() throws Exception {
		when(service.search("lon", 1)).thenReturn(new CitySearchResponse("lon", List.of()));
		when(service.search("lon", 10)).thenReturn(new CitySearchResponse("lon", List.of()));

		mvc.perform(get("/api/cities/search").param("query", "lon").param("limit", "1"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/cities/search").param("query", "lon").param("limit", "10"))
				.andExpect(status().isOk());
	}

	@Test
	void missingQueryIs400() throws Exception {
		mvc.perform(get("/api/cities/search")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Query parameter 'query' is required"));
		verifyNoInteractions(service);
	}

	@Test
	void blankQueryIs400() throws Exception {
		mvc.perform(get("/api/cities/search").param("query", "   ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Query parameter 'query' is required"));
		verifyNoInteractions(service);
	}

	@Test
	void oneCharQueryIs400() throws Exception {
		mvc.perform(get("/api/cities/search").param("query", " a ")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Query parameter 'query' must be at least 2 characters"));
		verifyNoInteractions(service);
	}

	@Test
	void limitZeroIs400() throws Exception {
		mvc.perform(get("/api/cities/search").param("query", "lon").param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Query parameter 'limit' must be between 1 and 10"));
		verifyNoInteractions(service);
	}

	@Test
	void limitElevenIs400() throws Exception {
		mvc.perform(get("/api/cities/search").param("query", "lon").param("limit", "11"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Query parameter 'limit' must be between 1 and 10"));
		verifyNoInteractions(service);
	}

	@Test
	void nonNumericLimitIs400() throws Exception {
		mvc.perform(get("/api/cities/search").param("query", "lon").param("limit", "abc"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Parameter 'limit' has an invalid value"));
		verifyNoInteractions(service);
	}

	@Test
	void upstreamFailureIs502() throws Exception {
		when(service.search("lon", 5)).thenThrow(new UpstreamServiceException("down"));

		mvc.perform(get("/api/cities/search").param("query", "lon")).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error").value("Bad Gateway"));
	}
}
