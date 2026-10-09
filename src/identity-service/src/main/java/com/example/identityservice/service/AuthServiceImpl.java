package com.example.identityservice.service;

import com.example.identityservice.client.DriverServiceClient;
import com.example.identityservice.dto.request.AccountRegisterRequest;
import com.example.identityservice.dto.request.CreateDriverRequest;
import com.example.identityservice.dto.request.DriverRegisterRequest;
import com.example.identityservice.dto.request.LoginRequest;
import com.example.identityservice.dto.request.PassengerRegisterRequest;
import com.example.identityservice.dto.response.DriverProfileResponse;
import com.example.identityservice.dto.response.DriverRegisterResponse;
import com.example.identityservice.dto.response.LoginResponse;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.BusinessException;
import com.example.identityservice.exception.ConflictException;
import com.example.identityservice.exception.DriverServiceRejectedException;
import com.example.identityservice.exception.ServiceUnavailableException;
import com.example.identityservice.mapper.UserMapper;
import com.example.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED";
    private static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final DriverServiceClient driverServiceClient;

    @Override
    @Transactional
    public UserResponse registerPassenger(PassengerRegisterRequest request) {
        User saved = createUser(request, UserRole.PASSENGER);
        log.info("User registered userId={}, role={}", saved.getUserId(), saved.getRole());
        return userMapper.toResponse(saved);
    }

    /**
     * Saga across identity_db and driver_db (docs/api.md, POST /api/auth/register/driver):
     * commit the user, create the driver profile in driver-service, and undo the user when that fails.
     * Not @Transactional on purpose: no database transaction is held open during the network call.
     */
    @Override
    public DriverRegisterResponse registerDriver(DriverRegisterRequest request) {
        User user = createUser(request, UserRole.DRIVER);
        UUID userId = user.getUserId();

        DriverProfileResponse profile;
        try {
            profile = driverServiceClient.createDriver(CreateDriverRequest.builder()
                    .userId(userId)
                    .licenseNumber(request.getLicenseNumber())
                    .vehicle(request.getVehicle())
                    .build());
        } catch (DriverServiceRejectedException ex) {
            // driver-service refused the request (invalid vehicle, plate taken...), so it created nothing
            deleteUser(userId);
            throw toClientError(ex);
        } catch (RuntimeException ex) {
            // Timeout, 5xx or unreadable answer: the profile may exist, so undo both sides
            log.warn("Driver profile creation outcome unknown for userId={}, compensating: {}", userId, ex.getMessage());
            deleteDriverProfile(userId);
            deleteUser(userId);
            throw new ServiceUnavailableException("Driver registration is temporarily unavailable, please try again later",
                    "DRIVER_SERVICE_UNAVAILABLE");
        }

        log.info("Driver registered userId={}, driverId={}", userId, profile.getDriverId());
        return userMapper.toDriverRegisterResponse(user, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Unknown email and wrong password return the same 401 so the response does not reveal which emails exist
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .filter(found -> passwordEncoder.matches(request.getPassword(), found.getPasswordHash()))
                .orElseThrow(() -> new BusinessException("Invalid email or password",
                        HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS));

        String accessToken = jwtService.generateToken(user);
        log.info("User logged in userId={}, role={}", user.getUserId(), user.getRole());
        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtService.getExpirationSeconds())
                .userId(user.getUserId())
                .role(user.getRole())
                .build();
    }

    private User createUser(AccountRegisterRequest request, UserRole role) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered", EMAIL_ALREADY_REGISTERED);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(role)
                .build();

        try {
            // Flush now so a concurrent registration hitting the unique email index surfaces here as 409, not at commit as 500
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Email already registered", EMAIL_ALREADY_REGISTERED);
        }
    }

    // 400 and 409 are the client's fault and are passed on; any other 4xx means identity-service is misconfigured
    private BusinessException toClientError(DriverServiceRejectedException ex) {
        if (ex.getStatus() == HttpStatus.BAD_REQUEST.value() || ex.getStatus() == HttpStatus.CONFLICT.value()) {
            return new BusinessException(ex.getMessage(), HttpStatus.valueOf(ex.getStatus()), ex.getCode());
        }
        log.error("driver-service rejected the internal call with {} {}; check DRIVER_SERVICE_URL and INTERNAL_API_KEY",
                ex.getStatus(), ex.getCode());
        return new BusinessException("Driver registration failed", HttpStatus.INTERNAL_SERVER_ERROR, "DRIVER_REGISTRATION_FAILED");
    }

    private void deleteDriverProfile(UUID userId) {
        try {
            driverServiceClient.deleteDriverByUserId(userId);
        } catch (RuntimeException ex) {
            log.error("Compensation failed: driver profile of userId={} may still exist in driver_db, delete it manually: {}",
                    userId, ex.getMessage());
        }
    }

    private void deleteUser(UUID userId) {
        try {
            userRepository.deleteById(userId);
            log.info("Compensation: user userId={} deleted after failed driver registration", userId);
        } catch (RuntimeException ex) {
            log.error("Compensation failed: user userId={} could not be deleted, delete it manually", userId, ex);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
