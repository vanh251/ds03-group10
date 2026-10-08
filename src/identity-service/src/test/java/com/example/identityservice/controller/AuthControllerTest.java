package com.example.identityservice.controller;

import com.example.identityservice.dto.request.RegisterRequest;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.BusinessException;
import com.example.identityservice.exception.ConflictException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String REGISTER_URL = "/api/auth/register";
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

    /** Builds a JSON body; a null value leaves the field out entirely. */
    private static String body(String fullName, String email, String password, String role) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (fullName != null) fields.put("fullName", fullName);
        if (email != null) fields.put("email", email);
        if (password != null) fields.put("password", password);
        if (role != null) fields.put("role", role);
        return JSON.writeValueAsString(fields);
    }

    private static String validBody() {
        return body("Nguyen Van A", "user@example.com", "S3cure-Pass!", "PASSENGER");
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
    @DisplayName("POST /api/auth/register with a valid body returns 201 with the profile and no password fields")
    void register_validRequest_returns201() throws Exception {
        UserResponse response = userResponse("Nguyen Van A", "user@example.com", UserRole.PASSENGER);
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("PASSENGER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture());
        assertEquals("user@example.com", captor.getValue().getEmail());
        assertEquals(UserRole.PASSENGER, captor.getValue().getRole());
    }

    static Stream<Arguments> boundaryPayloads() {
        return Stream.of(
                Arguments.of("fullName 100 chars, password 8 chars", "A".repeat(100), "p".repeat(8)),
                Arguments.of("fullName 1 char, password 72 chars", "A", "p".repeat(72))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundaryPayloads")
    @DisplayName("POST /api/auth/register accepts values exactly at the length limits")
    void register_boundaryValues_returns201(String caseName, String fullName, String password) throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(userResponse(fullName, "user@example.com", UserRole.DRIVER));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fullName, "user@example.com", password, "DRIVER")))
                .andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of("missing email", body("Nguyen Van A", null, "S3cure-Pass!", "PASSENGER")),
                Arguments.of("invalid email format", body("Nguyen Van A", "not-an-email", "S3cure-Pass!", "PASSENGER")),
                Arguments.of("password 7 chars", body("Nguyen Van A", "user@example.com", "p".repeat(7), "PASSENGER")),
                Arguments.of("password 73 chars", body("Nguyen Van A", "user@example.com", "p".repeat(73), "PASSENGER")),
                Arguments.of("missing password", body("Nguyen Van A", "user@example.com", null, "PASSENGER")),
                Arguments.of("blank fullName", body("   ", "user@example.com", "S3cure-Pass!", "PASSENGER")),
                Arguments.of("fullName 101 chars", body("A".repeat(101), "user@example.com", "S3cure-Pass!", "PASSENGER")),
                Arguments.of("missing role", body("Nguyen Van A", "user@example.com", "S3cure-Pass!", null))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    @DisplayName("POST /api/auth/register with an invalid field returns 400 VALIDATION_ERROR and never calls the service")
    void register_invalidPayload_returns400(String caseName, String payload) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-001")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value(REGISTER_URL))
                .andExpect(jsonPath("$.correlationId").value("corr-001"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/register with an unknown role value returns 400 MALFORMED_REQUEST")
    void register_unknownRole_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nguyen Van A", "user@example.com", "S3cure-Pass!", "ROOT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/register with malformed JSON returns 400 MALFORMED_REQUEST")
    void register_malformedJson_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/auth/register with an existing email returns 409 with the standard error body")
    void register_emailAlreadyRegistered_returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("Email already registered", "EMAIL_ALREADY_REGISTERED"));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "corr-409")
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.path").value(REGISTER_URL))
                .andExpect(jsonPath("$.correlationId").value("corr-409"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register with role ADMIN returns 400 ROLE_NOT_ALLOWED")
    void register_adminRole_returns400() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException("Role ADMIN cannot be self-registered",
                        HttpStatus.BAD_REQUEST, "ROLE_NOT_ALLOWED"));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Nguyen Van A", "admin@example.com", "S3cure-Pass!", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
    }
}
