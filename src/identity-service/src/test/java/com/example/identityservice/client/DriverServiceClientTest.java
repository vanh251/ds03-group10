package com.example.identityservice.client;

import com.example.identityservice.config.CorrelationIdFilter;
import com.example.identityservice.config.DriverServiceProperties;
import com.example.identityservice.dto.request.CreateDriverRequest;
import com.example.identityservice.dto.request.VehicleRequest;
import com.example.identityservice.dto.response.DriverProfileResponse;
import com.example.identityservice.exception.DriverServiceRejectedException;
import com.example.identityservice.exception.DriverServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DriverServiceClientTest {

    private static final String BASE_URL = "http://driver-service.test";
    private static final DriverServiceProperties PROPERTIES = new DriverServiceProperties(
            BASE_URL, Duration.ofSeconds(1), Duration.ofSeconds(2), "test-internal-api-key");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockRestServiceServer server;
    private DriverServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DriverServiceClient(builder, PROPERTIES);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private static CreateDriverRequest createRequest() {
        return CreateDriverRequest.builder()
                .userId(USER_ID)
                .fullName("Tran Van B")
                .licenseNumber("012345678901")
                .vehicle(VehicleRequest.builder()
                        .licensePlate("29A-12345").make("Toyota").model("Vios").color("White")
                        .year(2022).vehicleType("SEDAN")
                        .build())
                .build();
    }

    @Test
    @DisplayName("createDriver posts to /internal/drivers with the internal key and correlation id and reads the 201 body")
    void createDriver_created_returnsProfile() {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, "corr-123");
        UUID driverId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/drivers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(DriverServiceClient.INTERNAL_API_KEY_HEADER, "test-internal-api-key"))
                .andExpect(header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-123"))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.fullName").value("Tran Van B"))
                .andExpect(jsonPath("$.licenseNumber").value("012345678901"))
                .andExpect(jsonPath("$.vehicle.licensePlate").value("29A-12345"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"driverId": "%s", "userId": "%s", "status": "OFFLINE", "licenseNumber": "012345678901",
                         "vehicle": {"licensePlate": "29A-12345", "vehicleType": "SEDAN", "year": 2022},
                         "createdAt": "2026-10-09T10:00:00Z"}
                        """.formatted(driverId, USER_ID)));

        DriverProfileResponse profile = client.createDriver(createRequest());

        assertEquals(driverId, profile.getDriverId());
        assertEquals("OFFLINE", profile.getStatus());
        assertEquals("29A-12345", profile.getVehicle().getLicensePlate());
        server.verify();
    }

    @Test
    @DisplayName("createDriver without a correlation id in the MDC sends no X-Correlation-ID header")
    void createDriver_noCorrelationId_sendsNoHeader() {
        server.expect(requestTo(BASE_URL + "/internal/drivers"))
                .andExpect(headerDoesNotExist(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"driverId\": \"" + UUID.randomUUID() + "\", \"status\": \"OFFLINE\"}"));

        assertNotNull(client.createDriver(createRequest()));
        server.verify();
    }

    @Test
    @DisplayName("createDriver on a 409 throws DriverServiceRejectedException with driver-service's code and message")
    void createDriver_conflict_throwsRejected() {
        server.expect(requestTo(BASE_URL + "/internal/drivers"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"status": 409, "error": "Conflict", "message": "License plate already registered",
                         "code": "LICENSE_PLATE_ALREADY_REGISTERED", "path": "/internal/drivers"}
                        """));

        DriverServiceRejectedException ex = assertThrows(DriverServiceRejectedException.class,
                () -> client.createDriver(createRequest()));

        assertEquals(409, ex.getStatus());
        assertEquals("LICENSE_PLATE_ALREADY_REGISTERED", ex.getCode());
        assertEquals("License plate already registered", ex.getMessage());
    }

    @Test
    @DisplayName("createDriver on a 4xx without a JSON body still throws DriverServiceRejectedException with a default code")
    void createDriver_badRequestWithoutJson_throwsRejectedWithDefaultCode() {
        server.expect(requestTo(BASE_URL + "/internal/drivers"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("bad request"));

        DriverServiceRejectedException ex = assertThrows(DriverServiceRejectedException.class,
                () -> client.createDriver(createRequest()));

        assertEquals(400, ex.getStatus());
        assertEquals("DRIVER_SERVICE_REJECTED", ex.getCode());
    }

    @Test
    @DisplayName("createDriver on a 5xx throws DriverServiceUnavailableException (outcome unknown)")
    void createDriver_serverError_throwsUnavailable() {
        server.expect(requestTo(BASE_URL + "/internal/drivers")).andRespond(withServerError());

        assertThrows(DriverServiceUnavailableException.class, () -> client.createDriver(createRequest()));
    }

    @Test
    @DisplayName("createDriver on a read timeout throws DriverServiceUnavailableException (outcome unknown)")
    void createDriver_timeout_throwsUnavailable() {
        server.expect(requestTo(BASE_URL + "/internal/drivers")).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        DriverServiceUnavailableException ex = assertThrows(DriverServiceUnavailableException.class,
                () -> client.createDriver(createRequest()));

        assertTrue(ex.getMessage().contains("Read timed out"), ex.getMessage());
    }

    @Test
    @DisplayName("createDriver on a 201 without a driver profile throws DriverServiceUnavailableException")
    void createDriver_createdWithoutBody_throwsUnavailable() {
        server.expect(requestTo(BASE_URL + "/internal/drivers")).andRespond(withStatus(HttpStatus.CREATED));

        assertThrows(DriverServiceUnavailableException.class, () -> client.createDriver(createRequest()));
    }

    @Test
    @DisplayName("deleteDriverByUserId sends DELETE /internal/drivers?userId= with the internal key")
    void deleteDriverByUserId_noContent_succeeds() {
        server.expect(requestTo(BASE_URL + "/internal/drivers?userId=" + USER_ID))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(DriverServiceClient.INTERNAL_API_KEY_HEADER, "test-internal-api-key"))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> client.deleteDriverByUserId(USER_ID));
        server.verify();
    }

    @Test
    @DisplayName("deleteDriverByUserId on a 5xx throws so the caller can log the failed compensation")
    void deleteDriverByUserId_serverError_throws() {
        server.expect(requestTo(BASE_URL + "/internal/drivers?userId=" + USER_ID)).andRespond(withServerError());

        assertThrows(RestClientException.class, () -> client.deleteDriverByUserId(USER_ID));
    }

    @Test
    @DisplayName("the constructor refuses to start without an internal API key")
    void constructor_blankInternalApiKey_throwsIllegalState() {
        DriverServiceProperties withoutKey = new DriverServiceProperties(BASE_URL, Duration.ofSeconds(1), Duration.ofSeconds(2), " ");

        assertThrows(IllegalStateException.class, () -> new DriverServiceClient(RestClient.builder(), withoutKey));
    }
}
