package com.example.identityservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection to driver-service, bound from the {@code driver-service.*} keys in application.yml.
 *
 * @param url            base URL (Docker DNS, e.g. http://driver-service:8082)
 * @param connectTimeout TCP connect timeout
 * @param readTimeout    time to wait for the response
 * @param internalApiKey shared key sent as X-Internal-Api-Key on /internal/** calls
 */
@ConfigurationProperties(prefix = "driver-service")
public record DriverServiceProperties(String url, Duration connectTimeout, Duration readTimeout, String internalApiKey) {
}
