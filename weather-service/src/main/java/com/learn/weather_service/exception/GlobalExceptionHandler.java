package com.learn.weather_service.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<Map<String, String>> handleInvalid(InvalidRequestException ex) {
		return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
	}

	@ExceptionHandler(CityNotFoundException.class)
	ResponseEntity<Map<String, String>> handleNotFound(CityNotFoundException ex) {
		return body(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
	}

	@ExceptionHandler(UpstreamServiceException.class)
	ResponseEntity<Map<String, String>> handleUpstream(UpstreamServiceException ex) {
		return body(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return body(HttpStatus.BAD_REQUEST, "Bad Request", "Parameter '" + ex.getName() + "' has an invalid value");
	}

	private ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
		return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
	}
}
