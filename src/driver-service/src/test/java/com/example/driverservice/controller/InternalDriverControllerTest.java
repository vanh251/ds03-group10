package com.example.driverservice.controller;

import com.example.driverservice.config.CorrelationIdFilter;
import com.example.driverservice.config.InternalApiKeyInterceptor;
import com.example.driverservice.dto.request.CreateDriverRequest;
import com.example.driverservice.dto.response.DriverProfileResponse;
import com.example.driverservice.dto.response.VehicleResponse;
import com.example.driverservice.entity.DriverStatus;
import com.example.driverservice.entity.VehicleType;
import com.example.driverservice.exception.ConflictException;
import com.example.driverservice.exception.GlobalExceptionHandler;
import com.example.driverservice.service.DriverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalDriverControllerTest {

    private static final String URL = "/internal/drivers";
    private static final String API_KEY = "test-internal-api-key";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final JsonMapper JSON = new JsonMapper();

    private MockMvc mockMvc;

    @Mock
    private DriverService driverService;

    @InjectMocks
    private InternalDriverController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addMappedInterceptors(new String[]{"/internal/**"}, new InternalApiKeyInterceptor(API_KEY))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    private static Map<String, Object> vehicle(String plate, Object year, String type) {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        if (plate != null) vehicle.put("licensePlate", plate);
        vehicle.put("make", "Toyota");
        vehicle.put("model", "Vios");
        vehicle.put("color", "White");
        if (year != null) vehicle.put("year", year);
        if (type != null) vehicle.put("vehicleType", type);
        return vehicle;
    }

    /** Builds a JSON body; a null value leaves the field out entirely. */
    private static String body(UUID userId, String licenseNumber, Map<String, Object> vehicle) {
        return body(userId, "Tran Van B", licenseNumber, vehicle);
    }

    private static String body(UUID userId, String fullName, String licenseNumber, Map<String, Object> vehicle) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (userId != null) fields.put("userId", userId.toString());
        if (fullName != null) fields.put("fullName", fullName);
        if (licenseNumber != null) fields.put("licenseNumber", licenseNumber);
        if (vehicle != null) fields.put("vehicle", vehicle);
        return JSON.writeValueAsString(fields);
    }

    private static String validBody(String plate) {
        return body(USER_ID, "012345678901", vehicle(plate, 2022, "SEDAN"));
    }

    private DriverProfileResponse profile() {
        return DriverProfileResponse.builder()
                .driverId(USER_ID)
                .fullName("Tran Van B")
                .status(DriverStatus.OFFLINE)
                .licenseNumber("012345678901")
                .vehicle(VehicleResponse.builder()
                        .vehicleId(UUID.randomUUID())
                        .licensePlate("29A-12345")
                        .make("Toyota").model("Vios").color("White").year(2022)
                        .vehicleType(VehicleType.SEDAN)
                        .build())
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("POST /internal/drivers with the key and a valid body returns 201 with the profile")
    void createDriver_validRequest_returns201() throws Exception {
        DriverProfileResponse response = profile();
        when(driverService.createDriver(any(CreateDriverRequest.class))).thenReturn(response);

        mockMvc.perform(post(URL)
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("29A-12345")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.fullName").value("Tran Van B"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.vehicle.licensePlate").value("29A-12345"))
                .andExpect(jsonPath("$.vehicle.vehicleType").value("SEDAN"));

        ArgumentCaptor<CreateDriverRequest> captor = ArgumentCaptor.forClass(CreateDriverRequest.class);
        verify(driverService).createDriver(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals("Tran Van B", captor.getValue().getFullName());
        assertEquals(2022, captor.getValue().getVehicle().getYear());
    }

    @ParameterizedTest(name = "plate {0}")
    @ValueSource(strings = {"29A-12345", "29a-123.45", "29A-1234", "29B1-12345", "30LD-123.45"})
    @DisplayName("POST /internal/drivers accepts the Vietnamese plate formats")
    void createDriver_validPlateFormats_returns201(String plate) throws Exception {
        when(driverService.createDriver(any(CreateDriverRequest.class))).thenReturn(profile());

        mockMvc.perform(post(URL)
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(plate)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /internal/drivers without X-Internal-Api-Key returns 401 and never calls the service")
    void createDriver_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Correlation-ID", "corr-401")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("29A-12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_API_KEY"))
                .andExpect(jsonPath("$.correlationId").value("corr-401"));

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("DELETE /internal/drivers with a wrong X-Internal-Api-Key returns 401 and never calls the service")
    void deleteDriver_wrongApiKey_returns401() throws Exception {
        mockMvc.perform(delete(URL).param("userId", USER_ID.toString())
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_API_KEY"));

        verifyNoInteractions(driverService);
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of("missing userId", body(null, "012345678901", vehicle("29A-12345", 2022, "SEDAN"))),
                Arguments.of("missing fullName", body(USER_ID, null, "012345678901", vehicle("29A-12345", 2022, "SEDAN"))),
                Arguments.of("blank fullName", body(USER_ID, "  ", "012345678901", vehicle("29A-12345", 2022, "SEDAN"))),
                Arguments.of("license number 11 digits", body(USER_ID, "01234567890", vehicle("29A-12345", 2022, "SEDAN"))),
                Arguments.of("license number with letters", body(USER_ID, "01234567890A", vehicle("29A-12345", 2022, "SEDAN"))),
                Arguments.of("missing vehicle", body(USER_ID, "012345678901", null)),
                Arguments.of("missing plate", body(USER_ID, "012345678901", vehicle(null, 2022, "SEDAN"))),
                Arguments.of("plate without dash", body(USER_ID, "012345678901", vehicle("29A12345", 2022, "SEDAN"))),
                Arguments.of("plate 6 digits", body(USER_ID, "012345678901", vehicle("29A-123456", 2022, "SEDAN"))),
                Arguments.of("plate one-digit province", body(USER_ID, "012345678901", vehicle("2A-12345", 2022, "SEDAN"))),
                Arguments.of("year 1979", body(USER_ID, "012345678901", vehicle("29A-12345", 1979, "SEDAN"))),
                Arguments.of("missing year", body(USER_ID, "012345678901", vehicle("29A-12345", null, "SEDAN"))),
                Arguments.of("missing vehicle type", body(USER_ID, "012345678901", vehicle("29A-12345", 2022, null)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    @DisplayName("POST /internal/drivers with an invalid field returns 400 VALIDATION_ERROR and never calls the service")
    void createDriver_invalidPayload_returns400(String caseName, String payload) throws Exception {
        mockMvc.perform(post(URL)
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value(URL));

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("POST /internal/drivers with an unknown vehicleType returns 400 MALFORMED_REQUEST")
    void createDriver_unknownVehicleType_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(USER_ID, "012345678901", vehicle("29A-12345", 2022, "TRUCK"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("POST /internal/drivers with a plate already registered returns 409 with the service's code")
    void createDriver_plateConflict_returns409() throws Exception {
        when(driverService.createDriver(any(CreateDriverRequest.class)))
                .thenThrow(new ConflictException("License plate already registered", "LICENSE_PLATE_ALREADY_REGISTERED"));

        mockMvc.perform(post(URL)
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("29A-12345")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LICENSE_PLATE_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("License plate already registered"));
    }

    @Test
    @DisplayName("DELETE /internal/drivers?userId= with the key returns 204")
    void deleteDriver_validRequest_returns204() throws Exception {
        mockMvc.perform(delete(URL).param("userId", USER_ID.toString())
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(status().isNoContent());

        verify(driverService).deleteDriverByUserId(USER_ID);
    }

    @Test
    @DisplayName("DELETE /internal/drivers without userId returns 400 VALIDATION_ERROR")
    void deleteDriver_missingUserId_returns400() throws Exception {
        mockMvc.perform(delete(URL).header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(driverService);
    }

    @Test
    @DisplayName("DELETE /internal/drivers with a userId that is not a UUID returns 400 INVALID_PARAMETER")
    void deleteDriver_invalidUserId_returns400() throws Exception {
        mockMvc.perform(delete(URL).param("userId", "abc")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("DELETE /internal/drivers for a profile in use returns 409 DRIVER_PROFILE_IN_USE")
    void deleteDriver_profileInUse_returns409() throws Exception {
        doThrow(new ConflictException("Driver profile is in use and cannot be deleted", "DRIVER_PROFILE_IN_USE"))
                .when(driverService).deleteDriverByUserId(USER_ID);

        mockMvc.perform(delete(URL).param("userId", USER_ID.toString())
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_PROFILE_IN_USE"));
    }

    @Test
    @DisplayName("GET /internal/drivers (unsupported method) returns 405 METHOD_NOT_ALLOWED, not 500")
    void internalDrivers_wrongMethod_returns405() throws Exception {
        mockMvc.perform(get(URL).header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, API_KEY))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        verifyNoInteractions(driverService);
    }
}
