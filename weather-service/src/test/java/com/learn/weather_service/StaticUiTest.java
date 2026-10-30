package com.learn.weather_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StaticUiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootForwardsToIndex() throws Exception {
        // MockMvc does not follow the welcome-page forward, so assert the forward target.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void indexHtmlServed() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"q\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"results\"")));
    }

    @Test
    void appJsServed() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk());
    }

    @Test
    void indexHtmlContainsUnitToggle() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"unit-toggle\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-unit=\"C\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-unit=\"F\"")));
    }

    @Test
    void appJsContainsUnitStorageKey() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("weather.unit")));
    }

    @Test
    void appJsContainsCityStorageKey() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("weather.city")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("weather.unit")));
    }

    @Test
    void appJsPersistsAndRestoresCity() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("function loadCity")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("function saveCity")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("saveCity(city.name)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("loadForecast({ name: savedCity })")));
    }

    @Test
    void appJsSendsUnitsParam() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("units=")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("imperial")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("metric")));
    }

    @Test
    void appJsHasNoClientSideConversion() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9 / 5"))));
    }

    @Test
    void stylesServed() throws Exception {
        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk());
    }
}
