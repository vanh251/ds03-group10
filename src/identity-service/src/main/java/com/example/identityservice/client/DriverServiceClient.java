package com.example.identityservice.client;

import com.example.identityservice.config.CorrelationIdFilter;
import com.example.identityservice.config.DriverServiceProperties;
import com.example.identityservice.dto.request.CreateDriverRequest;
import com.example.identityservice.dto.response.DriverProfileResponse;
import com.example.identityservice.dto.response.ErrorResponse;
import com.example.identityservice.exception.DriverServiceRejectedException;
import com.example.identityservice.exception.DriverServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Calls the internal driver-service API (POST/DELETE /internal/drivers) used by driver registration.
 */
@Slf4j
@Component
@EnableConfigurationProperties(DriverServiceProperties.class)
public class DriverServiceClient {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_DRIVERS_PATH = "/internal/drivers";

    private final RestClient restClient;

    @Autowired
    public DriverServiceClient(DriverServiceProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory(properties)), properties);
    }

    // Package-private so tests can bind MockRestServiceServer to the builder
    DriverServiceClient(RestClient.Builder builder, DriverServiceProperties properties) {
        if (properties.internalApiKey() == null || properties.internalApiKey().isBlank()) {
            throw new IllegalStateException("driver-service.internal-api-key (INTERNAL_API_KEY) must be set");
        }
        this.restClient = builder
                .baseUrl(properties.url())
                .defaultHeader(INTERNAL_API_KEY_HEADER, properties.internalApiKey())
                .requestInterceptor((request, body, execution) -> {
                    // Forward the gateway's correlation ID so both services log the same request id
                    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(DriverServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    /**
     * Creates the driver profile.
     *
     * @throws DriverServiceRejectedException    on a 4xx: nothing was created
     * @throws DriverServiceUnavailableException on a timeout, network error, 5xx or unreadable body: outcome unknown
     */
    public DriverProfileResponse createDriver(CreateDriverRequest request) {
        try {
            return restClient.post()
                    .uri(INTERNAL_DRIVERS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (response.getStatusCode().is2xxSuccessful()) {
                            DriverProfileResponse profile = response.bodyTo(DriverProfileResponse.class);
                            if (profile == null || profile.getDriverId() == null) {
                                throw new DriverServiceUnavailableException("driver-service returned " + status + " without a driver profile", null);
                            }
                            return profile;
                        }
                        if (response.getStatusCode().is4xxClientError()) {
                            ErrorResponse error = readError(response);
                            throw new DriverServiceRejectedException(status,
                                    error != null && error.getCode() != null ? error.getCode() : "DRIVER_SERVICE_REJECTED",
                                    error != null && error.getMessage() != null ? error.getMessage() : "Driver profile was rejected");
                        }
                        throw new DriverServiceUnavailableException("driver-service returned " + status, null);
                    });
        } catch (RestClientException ex) {
            // Timeout or connection failure: the request may or may not have reached driver-service
            throw new DriverServiceUnavailableException("driver-service call failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Compensation: deletes the profile of the user if one exists. driver-service answers 204 either way.
     *
     * @throws RestClientException when driver-service cannot be reached or refuses the delete
     */
    public void deleteDriverByUserId(UUID userId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder.path(INTERNAL_DRIVERS_PATH).queryParam("userId", userId).build())
                .retrieve()
                .toBodilessEntity();
    }

    // The body is only used to pass driver-service's error code on; a body in another format is not an error here
    private ErrorResponse readError(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(ErrorResponse.class);
        } catch (RestClientException ex) {
            log.warn("Unreadable error body from driver-service: {}", ex.getMessage());
            return null;
        }
    }
}
