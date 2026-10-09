package com.example.identityservice.service;

import com.example.identityservice.client.DriverServiceClient;
import com.example.identityservice.dto.request.CreateDriverRequest;
import com.example.identityservice.dto.request.DriverRegisterRequest;
import com.example.identityservice.dto.request.LoginRequest;
import com.example.identityservice.dto.request.PassengerRegisterRequest;
import com.example.identityservice.dto.request.VehicleRequest;
import com.example.identityservice.dto.response.DriverProfileResponse;
import com.example.identityservice.dto.response.DriverRegisterResponse;
import com.example.identityservice.dto.response.LoginResponse;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.dto.response.VehicleResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.BusinessException;
import com.example.identityservice.exception.ConflictException;
import com.example.identityservice.exception.DriverServiceRejectedException;
import com.example.identityservice.exception.DriverServiceUnavailableException;
import com.example.identityservice.exception.ServiceUnavailableException;
import com.example.identityservice.mapper.UserMapper;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "S3cure-Pass!";

    @Mock
    private UserRepository userRepository;

    // Real BCrypt (low cost factor to keep the test fast) so the stored hash can be verified
    @Spy
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Spy
    private UserMapper userMapper = new UserMapper();

    @Mock
    private JwtService jwtService;

    @Mock
    private DriverServiceClient driverServiceClient;

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private PassengerRegisterRequest passengerRequest(String email) {
        return PassengerRegisterRequest.builder()
                .fullName("Nguyen Van A")
                .email(email)
                .password(RAW_PASSWORD)
                .build();
    }

    private DriverRegisterRequest driverRequest(String email) {
        return DriverRegisterRequest.builder()
                .fullName("Tran Van B")
                .email(email)
                .password(RAW_PASSWORD)
                .licenseNumber("012345678901")
                .vehicle(VehicleRequest.builder()
                        .licensePlate("29A-12345").make("Toyota").model("Vios").color("White")
                        .year(2022).vehicleType("SEDAN")
                        .build())
                .build();
    }

    /** Simulates the database assigning the id and timestamps on insert. */
    private void stubSaveAndFlushAssignsId() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(UUID.randomUUID());
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(user.getCreatedAt());
            return user;
        });
    }

    private User savedUser() {
        verify(userRepository).saveAndFlush(userCaptor.capture());
        return userCaptor.getValue();
    }

    @Test
    @DisplayName("registerPassenger with a valid request saves a PASSENGER with a BCrypt hash and returns the profile")
    void registerPassenger_validRequest_returnsUserResponse() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();

        UserResponse response = authService.registerPassenger(passengerRequest("user@example.com"));

        User saved = savedUser();
        assertNotEquals(RAW_PASSWORD, saved.getPasswordHash());
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, saved.getPasswordHash()));
        assertEquals("Nguyen Van A", saved.getFullName());
        assertEquals(UserRole.PASSENGER, saved.getRole());

        assertEquals(saved.getUserId(), response.getId());
        assertEquals("Nguyen Van A", response.getFullName());
        assertEquals("user@example.com", response.getEmail());
        assertEquals(UserRole.PASSENGER, response.getRole());
        assertNotNull(response.getCreatedAt());
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    @DisplayName("registerPassenger trims and lower-cases the email before the uniqueness check and save")
    void registerPassenger_emailWithUppercaseAndSpaces_storesNormalizedEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();

        UserResponse response = authService.registerPassenger(passengerRequest("  User@Example.COM "));

        verify(userRepository).existsByEmail("user@example.com");
        assertEquals("user@example.com", savedUser().getEmail());
        assertEquals("user@example.com", response.getEmail());
    }

    @Test
    @DisplayName("registerPassenger with an email that already exists throws 409 EMAIL_ALREADY_REGISTERED without saving")
    void registerPassenger_existingEmail_throwsConflict() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> authService.registerPassenger(passengerRequest("user@example.com")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("registerPassenger that loses a race on the unique email index throws 409 instead of 500")
    void registerPassenger_concurrentDuplicateEmail_uniqueViolation_throwsConflict() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> authService.registerPassenger(passengerRequest("user@example.com")));

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verify(userMapper, never()).toResponse(any());
    }

    private DriverProfileResponse driverProfile() {
        return DriverProfileResponse.builder()
                .driverId(UUID.randomUUID())
                .status("OFFLINE")
                .licenseNumber("012345678901")
                .vehicle(VehicleResponse.builder().licensePlate("29A-12345").vehicleType("SEDAN").build())
                .build();
    }

    @Test
    @DisplayName("registerDriver (4a) saves a DRIVER, creates the profile with that userId and returns both")
    void registerDriver_driverServiceCreatesProfile_returnsUserAndProfile() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        DriverProfileResponse profile = driverProfile();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class))).thenReturn(profile);

        DriverRegisterResponse response = authService.registerDriver(driverRequest("driver@example.com"));

        User saved = savedUser();
        assertEquals(UserRole.DRIVER, saved.getRole());
        ArgumentCaptor<CreateDriverRequest> createCaptor = ArgumentCaptor.forClass(CreateDriverRequest.class);
        verify(driverServiceClient).createDriver(createCaptor.capture());
        assertEquals(saved.getUserId(), createCaptor.getValue().getUserId());
        assertEquals("Tran Van B", createCaptor.getValue().getFullName());
        assertEquals("012345678901", createCaptor.getValue().getLicenseNumber());
        assertEquals("29A-12345", createCaptor.getValue().getVehicle().getLicensePlate());

        assertEquals(saved.getUserId(), response.getId());
        assertEquals(UserRole.DRIVER, response.getRole());
        assertEquals("driver@example.com", response.getEmail());
        assertSame(profile, response.getDriver());
        verify(userRepository, never()).deleteById(any());
        verify(driverServiceClient, never()).deleteDriverByUserId(any());
    }

    @Test
    @DisplayName("registerDriver with an existing email throws 409 before calling driver-service")
    void registerDriver_existingEmail_throwsConflictWithoutCallingDriverService() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> authService.registerDriver(driverRequest("driver@example.com")));

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verifyNoInteractions(driverServiceClient);
    }

    @Test
    @DisplayName("registerDriver (4b) when driver-service answers 409 deletes the user and passes the 409 code on")
    void registerDriver_plateConflict_deletesUserAndThrowsConflict() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class)))
                .thenThrow(new DriverServiceRejectedException(409, "LICENSE_PLATE_ALREADY_REGISTERED", "License plate already registered"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.registerDriver(driverRequest("driver@example.com")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("LICENSE_PLATE_ALREADY_REGISTERED", ex.getCode());
        assertEquals("License plate already registered", ex.getMessage());
        UUID userId = savedUser().getUserId();
        verify(userRepository).deleteById(userId);
        // A 4xx means nothing was created, so there is nothing to compensate in driver-service
        verify(driverServiceClient, never()).deleteDriverByUserId(any());
    }

    @Test
    @DisplayName("registerDriver (4b) when driver-service answers 400 deletes the user and passes the 400 code on")
    void registerDriver_invalidVehicle_deletesUserAndThrowsBadRequest() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class)))
                .thenThrow(new DriverServiceRejectedException(400, "VALIDATION_ERROR", "License plate format is invalid"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.registerDriver(driverRequest("driver@example.com")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
        UUID userId = savedUser().getUserId();
        verify(userRepository).deleteById(userId);
    }

    @Test
    @DisplayName("registerDriver when driver-service rejects the internal key (401) deletes the user and returns 500, not 401")
    void registerDriver_internalKeyRejected_deletesUserAndThrowsInternalError() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class)))
                .thenThrow(new DriverServiceRejectedException(401, "INVALID_INTERNAL_API_KEY", "Missing or invalid internal API key"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.registerDriver(driverRequest("driver@example.com")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals("DRIVER_REGISTRATION_FAILED", ex.getCode());
        verify(userRepository).deleteById(any());
    }

    @Test
    @DisplayName("registerDriver (4c) when driver-service times out deletes the profile, then the user, and returns 503")
    void registerDriver_driverServiceUnavailable_compensatesBothSidesAndThrows503() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class)))
                .thenThrow(new DriverServiceUnavailableException("driver-service call failed: Read timed out", null));

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> authService.registerDriver(driverRequest("driver@example.com")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("DRIVER_SERVICE_UNAVAILABLE", ex.getCode());
        UUID userId = savedUser().getUserId();
        InOrder inOrder = inOrder(driverServiceClient, userRepository);
        inOrder.verify(driverServiceClient).deleteDriverByUserId(userId);
        inOrder.verify(userRepository).deleteById(userId);
    }

    @Test
    @DisplayName("registerDriver (4c) still deletes the user and returns 503 when the profile delete also fails")
    void registerDriver_compensatingDeleteFails_stillDeletesUserAndThrows503() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class)))
                .thenThrow(new DriverServiceUnavailableException("driver-service returned 503", null));
        doThrow(new RestClientException("Connection refused")).when(driverServiceClient).deleteDriverByUserId(any());

        assertThrows(ServiceUnavailableException.class,
                () -> authService.registerDriver(driverRequest("driver@example.com")));

        UUID userId = savedUser().getUserId();
        verify(userRepository).deleteById(userId);
    }

    @Test
    @DisplayName("registerDriver keeps the original error when deleting the user also fails")
    void registerDriver_userDeleteFails_stillThrowsOriginalError() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();
        when(driverServiceClient.createDriver(any(CreateDriverRequest.class)))
                .thenThrow(new DriverServiceRejectedException(409, "LICENSE_PLATE_ALREADY_REGISTERED", "License plate already registered"));
        doThrow(new DataAccessResourceFailureException("connection lost")).when(userRepository).deleteById(any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.registerDriver(driverRequest("driver@example.com")));

        assertEquals("LICENSE_PLATE_ALREADY_REGISTERED", ex.getCode());
    }

    private User existingUser(String email, UserRole role) {
        return User.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("Tran Van Binh")
                .role(role)
                .build();
    }

    private LoginRequest loginRequest(String email, String password) {
        return LoginRequest.builder().email(email).password(password).build();
    }

    @Test
    @DisplayName("login with the right password returns a Bearer token with userId, role and lifetime")
    void login_validCredentials_returnsToken() {
        User user = existingUser("driver01@ds03.test", UserRole.DRIVER);
        when(userRepository.findByEmail("driver01@ds03.test")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("signed.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(86400L);

        LoginResponse response = authService.login(loginRequest("driver01@ds03.test", RAW_PASSWORD));

        assertEquals("signed.jwt.token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(86400L, response.getExpiresIn());
        assertEquals(user.getUserId(), response.getUserId());
        assertEquals(UserRole.DRIVER, response.getRole());
    }

    @Test
    @DisplayName("login trims and lower-cases the email before looking the user up")
    void login_emailWithUppercaseAndSpaces_looksUpNormalizedEmail() {
        User user = existingUser("driver01@ds03.test", UserRole.DRIVER);
        when(userRepository.findByEmail("driver01@ds03.test")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("signed.jwt.token");

        authService.login(loginRequest("  Driver01@DS03.test ", RAW_PASSWORD));

        verify(userRepository).findByEmail("driver01@ds03.test");
    }

    @Test
    @DisplayName("login with a wrong password throws 401 INVALID_CREDENTIALS and issues no token")
    void login_wrongPassword_throwsUnauthorized() {
        User user = existingUser("driver01@ds03.test", UserRole.DRIVER);
        when(userRepository.findByEmail("driver01@ds03.test")).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("driver01@ds03.test", "wrong-password")));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("login with an unknown email throws the same 401 INVALID_CREDENTIALS as a wrong password")
    void login_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("nobody@ds03.test")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("nobody@ds03.test", RAW_PASSWORD)));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        assertEquals("Invalid email or password", ex.getMessage());
        verify(jwtService, never()).generateToken(any());
    }
}
