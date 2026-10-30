package com.learn.weather_service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.learn.weather_service.exception.InvalidRequestException;

class UnitsTest {

	@Test
	void nullDefaultsToMetric() {
		assertThat(Units.parse(null)).isEqualTo(Units.METRIC);
	}

	@Test
	void parsesMetricAndImperial() {
		assertThat(Units.parse("metric")).isEqualTo(Units.METRIC);
		assertThat(Units.parse("imperial")).isEqualTo(Units.IMPERIAL);
	}

	@Test
	void parseIsCaseInsensitive() {
		assertThat(Units.parse("IMPERIAL")).isEqualTo(Units.IMPERIAL);
		assertThat(Units.parse("Metric")).isEqualTo(Units.METRIC);
	}

	@Test
	void parseTrimsWhitespace() {
		assertThat(Units.parse(" metric ")).isEqualTo(Units.METRIC);
	}

	@Test
	void unknownValueThrows() {
		assertThatThrownBy(() -> Units.parse("kelvin")).isInstanceOf(InvalidRequestException.class)
				.hasMessage("Query parameter 'units' must be 'metric' or 'imperial'");
	}

	@Test
	void emptyAndBlankThrow() {
		assertThatThrownBy(() -> Units.parse("")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> Units.parse("   ")).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void metricCarriesCorrectValues() {
		assertThat(Units.METRIC.temperatureParam()).isEqualTo("celsius");
		assertThat(Units.METRIC.precipitationParam()).isEqualTo("mm");
		assertThat(Units.METRIC.temperatureLabel()).isEqualTo("°C");
		assertThat(Units.METRIC.precipitationLabel()).isEqualTo("mm");
		assertThat(Units.METRIC.id()).isEqualTo("metric");
	}

	@Test
	void imperialCarriesCorrectValues() {
		assertThat(Units.IMPERIAL.temperatureParam()).isEqualTo("fahrenheit");
		assertThat(Units.IMPERIAL.precipitationParam()).isEqualTo("inch");
		assertThat(Units.IMPERIAL.temperatureLabel()).isEqualTo("°F");
		assertThat(Units.IMPERIAL.precipitationLabel()).isEqualTo("in");
		assertThat(Units.IMPERIAL.id()).isEqualTo("imperial");
	}
}
