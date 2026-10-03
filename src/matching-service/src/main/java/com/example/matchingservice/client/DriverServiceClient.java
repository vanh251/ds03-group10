package com.example.matchingservice.client;

import com.example.matchingservice.dto.AvailableDriverDto;
import com.example.matchingservice.dto.ReserveDriverRequest;
import com.example.matchingservice.dto.ReserveDriverResponse;
import com.example.matchingservice.exception.DriverReservationConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class DriverServiceClient {

    private final RestClient restClient;

    public DriverServiceClient(
            @Value("${driver-service.url:http://driver-service:8082}") String driverServiceUrl,
            RestClient.Builder restClientBuilder) {
        log.info("Configuring DriverServiceClient with baseUrl: {}", driverServiceUrl);
        this.restClient = restClientBuilder.baseUrl(driverServiceUrl).build();
    }

    public List<AvailableDriverDto> getAvailableDrivers(Double lat, Double lng, Double radiusKm) {
        try {
            log.info("Calling Driver Service GET /api/drivers/available lat={}, lng={}, radiusKm={}", lat, lng, radiusKm);
            List<AvailableDriverDto> drivers = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/drivers/available")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lng)
                            .queryParam("radiusKm", radiusKm != null ? radiusKm : 5.0)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AvailableDriverDto>>() {});

            return drivers != null ? drivers : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Error fetching available drivers from driver-service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public ReserveDriverResponse reserveDriver(UUID driverId, UUID tripId, Integer expectedVersion) {
        log.info("Attempting CAS reservation for driver={}, tripId={}, expectedVersion={}", driverId, tripId, expectedVersion);
        ReserveDriverRequest request = ReserveDriverRequest.builder()
                .tripId(tripId)
                .expectedVersion(expectedVersion)
                .build();

        return restClient.post()
                .uri("/api/drivers/{id}/reserve", driverId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 409, (req, resp) -> {
                    log.warn("Driver reservation conflict 409 for driver={}", driverId);
                    throw new DriverReservationConflictException(driverId);
                })
                .body(ReserveDriverResponse.class);
    }

    public void releaseDriverReservation(UUID driverId) {
        log.info("Releasing reservation for driver={}", driverId);
        try {
            restClient.delete()
                    .uri("/api/drivers/{id}/reserve", driverId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Error releasing driver reservation for driver {}: {}", driverId, e.getMessage());
        }
    }
}
