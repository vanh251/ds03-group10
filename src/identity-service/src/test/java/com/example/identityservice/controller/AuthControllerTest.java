package com.example.identityservice.controller;

import com.example.identityservice.dto.request.DriverRegisterRequest;
import com.example.identityservice.dto.request.LoginRequest;
import com.example.identityservice.dto.request.PassengerRegisterRequest;
import com.example.identityservice.dto.response.DriverProfileResponse;
import com.example.identityservice.dto.response.DriverRegisterResponse;
import com.example.identityservice.dto.response.LoginResponse;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.dto.response.VehicleResponse;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.BusinessException;
import com.example.identityservice.exception.ConflictException;
import com.example.identityservice.exception.ServiceUnavailableException;
import com.example.identityservice.exception.GlobalExceptionHandler;
import com.example.identityservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String PASSENGER_URL = "/api/auth/register/passenger";
    private static final String DRIVER_URL = "/api/auth/register/driver";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final JsonMapper JSON = new JsonMapper();

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** Builds the account part of a JSON body; a null value leaves the field out entirely. */
    private static Map<String, Object> account(String fullName, String email, String password) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (fullName != null) fields.put("fullName", fullName);
        if (email != null) fields.put("email", email);
        if (password != null) fields.put("password", password);
        return fields;
    }

    private static String passengerBody(String fullName, String email, String password) {
        return JSON.writeValueAsString(account(fullName, email, password));
    }

    private static String validPassengerBody() {
        return passengerBody("Nguyen Van A", "user@example.com", "S3cure-Pass!");
    }

    private static Map<String, Object> vehicle(String plate, Integer year) {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        if (plate != null) vehicle.put("licensePlate", plate);
        vehicle.put("make", "Toyota");
        vehicle.put("model", "Vios");
        vehicle.put("color", "White");
        if (year != null) vehicle.put("year", year);
        vehicle.put("vehicleType", "SEDAN");
        return vehicle;
    }

    private static String driverBody(String licenseNumber, Map<String, Object> vehicle) {
        Map<String, Object> fields = account("Tran Van B", "driver@example.com", "S3cure-Pass!");
        if (licenseNumber != null) fields.put("licenseNumber", licenseNumber);
        if (vehicle != null) fields.put("vehicle", vehicle);
        return JSON.writeValueAsString(fields);
    }

    private static String validDriverBody() {
        return driverBody("012345678901", vehicle("29A-12345", 2022));
    }

    private UserResponse userResponse(String fullName, String email, UserRole role) {
        return UserResponse.builder()
                .id(UUID.randomUUID())
                .fullName(fullName)
                .email(email)
                .role(role)
                .createdAt(Instant.parse("2026-10-08T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/register/passenger with a valid body returns 201 with the profile and no password fields")
    void registerPassenger_validRequest_returns201() throws Exception {
        UserResponse response = userResponse("Nguyen Van A", "user@example.com", UserRole.PASSENGER);
        when(authService.registerPassenger(any(PassengerRegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post(PASSENGER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPassengerBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("PASSENGER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<PassengerRegisterRequest> captor = ArgumentCaptor.forClass(PassengerRegisterRequest.class);
        verify(authService).registerPassenger(captor.capture());
        assertEquals("user@example.com", captor.getValue().getEmail());
        assertEquals("S3cure-Pass!", captor.getValue().getPassword());
    }

    static Stream<Arguments> boundaryPayloads() {
        return Stream.of(
                Arguments.of("fullName 100 chars, password 8 chars", "A".repeat(100), "p".repeat(8)),
                Arguments.of("fullName 1 char, password 72 chars", "A", "p".repeat(72))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundaryPayloads")
    @DisplayName("POST /api/auth/register/passenger accepts values exactly at the length limits")
    void registerPassenger_boundaryValues_returns201(String caseName, String fullName, String password) throws Exception {
        when(authService.registerPassenger(any(PassengerRegisterRequest.class)))
                .thenReturn(userResponse(fullName, "user@example.com", UserRole.PASSENGER));

        mockMvc.perform(post(PASSENGER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passengerBody(fullName, "user@example.com", password)))
                .andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidPassengerPayloads() {
        return Stream.of(
                Arguments.of("missing email", passengerBody("Nguyen Van A", null, "S3cure-Pass!")),
                Arguments.of("invalid email format", passengerBody("Nguyen Van A", "not-an-email", "S3cure-Pass!")),
                Arguments.of("password 7 chars", passengerBody("Nguyen Van A", "user@example.com", "p".repeat(7))),
                Arguments.of("password 73 chars", passengerBody("Nguyen Van A", "user@example.com", "p".repeat(73))),
                Arguments.of("missing password", passengerBody("Nguyen Van A", "user@example.com", null)),
                Arguments.of("blank fullName", passengerBody("   ", "user@example.com", "S3cure-Pass!")),
                Arguments.of("fullName 101 chars", passengerBody("A".repeat(101), "user@example.com", "S3cure-Pass!"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPassengerPayloads")
    @DisplayName("POST /api/auth/register/passenger with an invalid field returns 400 VALIDATION_ERROR and never calls the service")
    void registerPassenger_invalidPayload_returns400(String caseName, String payload) throws Exception {
        mockMvc.perform(post(PASSENGER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-001")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value(PASSENGER_URL))
                .andExpect(jsonPath("$.correlationId").value("corr-001"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/register/passenger with malformed JSON returns 400 MALFORMED_REQUEST")
    void registerPassenger_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(PASSENGER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/register/passenger with an existing email returns 409 with the standard error body")
    void registerPassenger_emailAlreadyRegistered_returns409() throws Exception {
        when(authService.registerPassenger(any(PassengerRegisterRequest.class)))
                .thenThrow(new ConflictException("Email already registered", "EMAIL_ALREADY_REGISTERED"));

        mockMvc.perform(post(PASSENGER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-409")
                        .content(validPassengerBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.path").value(PASSENGER_URL))
                .andExpect(jsonPath("$.correlationId").value("corr-409"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register (old path) returns 404 NOT_FOUND, not 500")
    void register_oldPath_returns404() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPassengerBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("GET /api/auth/login (wrong method) returns 405 METHOD_NOT_ALLOWED, not 500")
    void login_wrongMethod_returns405() throws Exception {
        mockMvc.perform(get(LOGIN_URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("POST /api/auth/register/driver with a valid body returns 201 with the account and the driver profile")
    void registerDriver_validRequest_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        when(authService.registerDriver(any(DriverRegisterRequest.class))).thenReturn(DriverRegisterResponse.builder()
                .id(userId)
                .fullName("Tran Van B")
                .email("driver@example.com")
                .role(UserRole.DRIVER)
                .createdAt(Instant.parse("2026-10-09T10:00:00Z"))
                .driver(DriverProfileResponse.builder()
                        .driverId(driverId)
                        .status("OFFLINE")
                        .licenseNumber("012345678901")
                        .vehicle(VehicleResponse.builder().licensePlate("29A-12345").vehicleType("SEDAN").year(2022).build())
                        .build())
                .build());

        mockMvc.perform(post(DRIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDriverBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.driver.driverId").value(driverId.toString()))
                .andExpect(jsonPath("$.driver.status").value("OFFLINE"))
                .andExpect(jsonPath("$.driver.vehicle.licensePlate").value("29A-12345"))
                .andExpect(jsonPath("$.password").doesNotExist());

        ArgumentCaptor<DriverRegisterRequest> captor = ArgumentCaptor.forClass(DriverRegisterRequest.class);
        verify(authService).registerDriver(captor.capture());
        assertEquals("driver@example.com", captor.getValue().getEmail());
        assertEquals("012345678901", captor.getValue().getLicenseNumber());
        assertEquals("29A-12345", captor.getValue().getVehicle().getLicensePlate());
        assertEquals(2022, captor.getValue().getVehicle().getYear());
    }

    static Stream<Arguments> invalidDriverPayloads() {
        return Stream.of(
                Arguments.of("missing vehicle", driverBody("012345678901", null)),
                Arguments.of("missing license number", driverBody(null, vehicle("29A-12345", 2022))),
                Arguments.of("missing license plate", driverBody("012345678901", vehicle(null, 2022))),
                Arguments.of("missing vehicle year", driverBody("012345678901", vehicle("29A-12345", null)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDriverPayloads")
    @DisplayName("POST /api/auth/register/driver without the driver fields returns 400 before any account is created")
    void registerDriver_missingDriverFields_returns400(String caseName, String payload) throws Exception {
        mockMvc.perform(post(DRIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value(DRIVER_URL));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/register/driver with a plate already registered returns driver-service's 409 code")
    void registerDriver_plateConflict_returns409() throws Exception {
        when(authService.registerDriver(any(DriverRegisterRequest.class)))
                .thenThrow(new BusinessException("License plate already registered", HttpStatus.CONFLICT, "LICENSE_PLATE_ALREADY_REGISTERED"));

        mockMvc.perform(post(DRIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDriverBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LICENSE_PLATE_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("POST /api/auth/register/driver when driver-service is down returns 503 DRIVER_SERVICE_UNAVAILABLE")
    void registerDriver_driverServiceUnavailable_returns503() throws Exception {
        when(authService.registerDriver(any(DriverRegisterRequest.class)))
                .thenThrow(new ServiceUnavailableException("Driver registration is temporarily unavailable, please try again later",
                        "DRIVER_SERVICE_UNAVAILABLE"));

        mockMvc.perform(post(DRIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-503")
                        .content(validDriverBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("DRIVER_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").value("corr-503"));
    }

    private static String loginBody(String email, String password) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (email != null) fields.put("email", email);
        if (password != null) fields.put("password", password);
        return JSON.writeValueAsString(fields);
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials returns 200 with the token fields from docs/api.md")
    void login_validCredentials_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class))).thenReturn(LoginResponse.builder()
                .accessToken("signed.jwt.token")
                .tokenType("Bearer")
                .expiresIn(86400)
                .userId(userId)
                .role(UserRole.DRIVER)
                .build());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("driver01@ds03.test", "S3cure-Pass!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("DRIVER"));
    }

    static Stream<Arguments> invalidLoginPayloads() {
        return Stream.of(
                Arguments.of("missing email", loginBody(null, "S3cure-Pass!")),
                Arguments.of("invalid email format", loginBody("not-an-email", "S3cure-Pass!")),
                Arguments.of("missing password", loginBody("driver01@ds03.test", null)),
                Arguments.of("blank password", loginBody("driver01@ds03.test", "   ")),
                Arguments.of("password 73 chars", loginBody("driver01@ds03.test", "p".repeat(73)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLoginPayloads")
    @DisplayName("POST /api/auth/login with an invalid field returns 400 VALIDATION_ERROR and never calls the service")
    void login_invalidPayload_returns400(String caseName, String payload) throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value(LOGIN_URL));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong credentials returns 401 INVALID_CREDENTIALS")
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-401")
                        .content(loginBody("driver01@ds03.test", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.correlationId").value("corr-401"));
    }
}
