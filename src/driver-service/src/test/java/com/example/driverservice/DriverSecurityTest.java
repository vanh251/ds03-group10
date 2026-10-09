package com.example.driverservice;

import com.example.driverservice.config.InternalApiKeyInterceptor;
import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.DriverStatus;
import com.example.driverservice.repository.DriverRepository;
import com.example.driverservice.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the driver APIs through the real Spring Security chain and JwtAuthenticationFilter with tokens shaped like
 * identity-service's; only the repositories are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DriverSecurityTest {

    private static final UUID DRIVER_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverRepository driverRepository;

    @MockitoBean
    private VehicleRepository vehicleRepository;

    private static String statusBody(String status) {
        return "{\"status\": \"" + status + "\"}";
    }

    @Test
    @DisplayName("GET /api/drivers/available is reachable without a token (matching-service calls it without one)")
    void getAvailable_withoutToken_returns200() throws Exception {
        when(driverRepository.findByStatus(DriverStatus.AVAILABLE)).thenReturn(List.of());

        mockMvc.perform(get("/api/drivers/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("PATCH status with the driver's own DRIVER token returns 200")
    void updateStatus_ownDriverToken_returns200() throws Exception {
        Driver driver = Driver.builder().driverId(DRIVER_ID).fullName("Tran Van Binh").status(DriverStatus.OFFLINE)
                .version(1).updatedAt(Instant.now()).build();
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
        when(driverRepository.saveAndFlush(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.token(DRIVER_ID, "DRIVER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("AVAILABLE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("PATCH status without a token returns 401 MISSING_TOKEN")
    void updateStatus_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .header("X-Correlation-ID", "corr-401")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("AVAILABLE")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("MISSING_TOKEN"))
                .andExpect(jsonPath("$.correlationId").value("corr-401"));

        verifyNoInteractions(driverRepository);
    }

    @Test
    @DisplayName("PATCH status with an expired token returns 401 TOKEN_EXPIRED")
    void updateStatus_expiredToken_returns401() throws Exception {
        String expired = TestTokens.token(TestTokens.TEST_SECRET, DRIVER_ID.toString(), "DRIVER", Instant.now().minusSeconds(5));

        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(expired))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("AVAILABLE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("PATCH status with a token signed by another key returns 401 INVALID_TOKEN")
    void updateStatus_foreignToken_returns401() throws Exception {
        String foreign = TestTokens.token(TestTokens.OTHER_SECRET, DRIVER_ID.toString(), "DRIVER", Instant.now().plusSeconds(60));

        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(foreign))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("AVAILABLE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("PATCH status with a token whose role is unknown returns 401 INVALID_TOKEN")
    void updateStatus_unknownRole_returns401() throws Exception {
        String token = TestTokens.token(TestTokens.TEST_SECRET, DRIVER_ID.toString(), "ROOT", Instant.now().plusSeconds(60));

        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("AVAILABLE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("PATCH status with a PASSENGER token returns 403 ACCESS_DENIED")
    void updateStatus_passengerToken_returns403() throws Exception {
        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.token(DRIVER_ID, "PASSENGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("AVAILABLE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(driverRepository);
    }

    @Test
    @DisplayName("PATCH status of another driver with a DRIVER token returns 403 NOT_OWN_RESOURCE")
    void updateStatus_otherDriversProfile_returns403() throws Exception {
        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.token(UUID.randomUUID(), "DRIVER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("AVAILABLE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWN_RESOURCE"));

        verifyNoInteractions(driverRepository);
    }

    @Test
    @DisplayName("/internal/** is not opened by the JWT chain: without X-Internal-Api-Key it is still 401")
    void internalApi_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/internal/drivers")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.token(DRIVER_ID, "DRIVER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_API_KEY"));
    }

    @Test
    @DisplayName("/internal/** with the internal key reaches the controller (validation answers 400)")
    void internalApi_withApiKey_reachesController() throws Exception {
        mockMvc.perform(post("/internal/drivers")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "test-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
