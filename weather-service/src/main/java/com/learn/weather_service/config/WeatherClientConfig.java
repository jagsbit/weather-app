package com.learn.weather_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties(WeatherApiProperties.class)
public class WeatherClientConfig {

	@Bean
	RestClientCustomizer weatherRestClientCustomizer(WeatherApiProperties properties) {
		return builder -> {
			SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
			factory.setConnectTimeout(properties.connectTimeout());
			factory.setReadTimeout(properties.readTimeout());
			builder.requestFactory(factory);
		};
	}
}
