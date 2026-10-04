package com.example.tripservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class PricingServiceClient {

    private final RestClient restClient;

    public PricingServiceClient(
            @Value("${pricing-service.url:http://pricing-service:8085}") String pricingServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(pricingServiceUrl).build();
    }

    public BigDecimal getFinalFare(UUID tripId, double distanceKm, int durationMin) {
        log.info("Calling Pricing Service POST /api/pricing/final for trip={}", tripId);
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/pricing/final")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "tripId", tripId,
                            "actualDistanceKm", distanceKm,
                            "actualDurationMin", durationMin
                    ))
                    .retrieve()
                    .body(Map.class);
            
            if (response != null && response.containsKey("totalFare")) {
                return new BigDecimal(response.get("totalFare").toString());
            }
        } catch (Exception e) {
            log.warn("Error calling Pricing Service for final fare: {}", e.getMessage());
        }
        return null;
    }
}
