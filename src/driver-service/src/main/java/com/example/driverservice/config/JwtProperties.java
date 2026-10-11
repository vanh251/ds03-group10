package com.example.driverservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from the {@code jwt.*} keys in application.yml.
 *
 * @param secret Base64-encoded HMAC key; must be the same JWT_SECRET identity-service signs tokens with
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {
}
