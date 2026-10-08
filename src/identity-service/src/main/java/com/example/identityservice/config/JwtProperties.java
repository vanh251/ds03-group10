package com.example.identityservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from the {@code jwt.*} keys in application.yml.
 *
 * @param secret       Base64-encoded HMAC key (at least 256 bits for HS256)
 * @param expirationMs access token lifetime in milliseconds
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs) {
}
