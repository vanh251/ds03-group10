package com.example.driverservice.controller;

import com.example.driverservice.dto.response.AvailableDriverResponse;
import com.example.driverservice.dto.response.DriverStatusResponse;
import com.example.driverservice.dto.response.LocationResponse;
import com.example.driverservice.entity.DriverStatus;
import com.example.driverservice.exception.BusinessException;
import com.example.driverservice.exception.GlobalExceptionHandler;
import com.example.driverservice.service.DriverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    private static final UUID DRIVER_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");

    private MockMvc mockMvc;

    @Mock
    private DriverService driverService;

    @InjectMocks
    private DriverController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        // What JwtAuthenticationFilter puts in the context for a DRIVER token
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                DRIVER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/drivers/available returns the fields matching-service reads (id, fullName, status, location, distanceKm, version)")
    void getAvailableDrivers_withPoint_returnsMatchingFields() throws Exception {
        when(driverService.findAvailableDrivers(21.0285, 105.8542, 3.0)).thenReturn(List.of(AvailableDriverResponse.builder()
                .id(DRIVER_ID)
                .fullName("Tran Van Binh")
                .status(DriverStatus.AVAILABLE)
                .location(LocationResponse.builder().latitude(21.025).longitude(105.857)
                        .updatedAt(Instant.parse("2026-10-09T10:00:00Z")).build())
                .distanceKm(0.49)
                .version(3)
                .build()));

        mockMvc.perform(get("/api/drivers/available")
                        .param("latitude", "21.0285").param("longitude", "105.8542").param("radiusKm", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(DRIVER_ID.toString()))
                .andExpect(jsonPath("$[0].fullName").value("Tran Van Binh"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].location.latitude").value(21.025))
                .andExpect(jsonPath("$[0].location.longitude").value(105.857))
                .andExpect(jsonPath("$[0].distanceKm").value(0.49))
                .andExpect(jsonPath("$[0].version").value(3));
    }

    @Test
    @DisplayName("GET /api/drivers/available without parameters uses no point and the default 5 km radius")
    void getAvailableDrivers_noParams_usesDefaults() throws Exception {
        when(driverService.findAvailableDrivers(null, null, 5.0)).thenReturn(List.of());

        mockMvc.perform(get("/api/drivers/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(driverService).findAvailableDrivers(isNull(), isNull(), eq(5.0));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"latitude, 91", "latitude, -90.5", "longitude, 180.1", "radiusKm, 0", "radiusKm, -1", "radiusKm, 50.5"})
    @DisplayName("GET /api/drivers/available with an out-of-range parameter returns 400 VALIDATION_ERROR")
    void getAvailableDrivers_outOfRange_returns400(String name, String value) throws Exception {
        var request = get("/api/drivers/available").param(name, value);
        if (!name.equals("latitude")) request.param("latitude", "21.0");
        if (!name.equals("longitude")) request.param("longitude", "105.8");

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(driverService, never()).findAvailableDrivers(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("GET /api/drivers/available with a non-numeric latitude returns 400 INVALID_PARAMETER")
    void getAvailableDrivers_nonNumeric_returns400() throws Exception {
        mockMvc.perform(get("/api/drivers/available").param("latitude", "abc").param("longitude", "105.8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("PATCH /api/drivers/{id}/status passes the token's user id to the service and returns the new status")
    void updateStatus_validRequest_returns200() throws Exception {
        when(driverService.updateStatus(DRIVER_ID, DriverStatus.AVAILABLE, DRIVER_ID)).thenReturn(DriverStatusResponse.builder()
                .id(DRIVER_ID).status(DriverStatus.AVAILABLE).updatedAt(Instant.parse("2026-10-09T10:10:00Z")).build());

        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"AVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DRIVER_ID.toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("PATCH /api/drivers/{id}/status without status returns 400 VALIDATION_ERROR")
    void updateStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("PATCH /api/drivers/{id}/status with an unknown status value returns 400 MALFORMED_REQUEST")
    void updateStatus_unknownStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SLEEPING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("PATCH /api/drivers/{id}/status with a forbidden transition returns 422")
    void updateStatus_invalidTransition_returns422() throws Exception {
        when(driverService.updateStatus(DRIVER_ID, DriverStatus.BUSY, DRIVER_ID)).thenThrow(new BusinessException(
                "Status cannot be changed from AVAILABLE to BUSY", HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_STATUS_TRANSITION"));

        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"BUSY\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("PATCH /api/drivers/{id}/status that loses a concurrent update returns 409 CONCURRENT_UPDATE")
    void updateStatus_optimisticLockFailure_returns409() throws Exception {
        when(driverService.updateStatus(DRIVER_ID, DriverStatus.OFFLINE, DRIVER_ID))
                .thenThrow(new ObjectOptimisticLockingFailureException("Driver", DRIVER_ID));

        mockMvc.perform(patch("/api/drivers/{id}/status", DRIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"OFFLINE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    }
}
