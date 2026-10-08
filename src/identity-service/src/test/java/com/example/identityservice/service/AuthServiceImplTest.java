package com.example.identityservice.service;

import com.example.identityservice.dto.request.RegisterRequest;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.BusinessException;
import com.example.identityservice.exception.ConflictException;
import com.example.identityservice.mapper.UserMapper;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
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

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private RegisterRequest request(String email, UserRole role) {
        return RegisterRequest.builder()
                .fullName("Nguyen Van A")
                .email(email)
                .password(RAW_PASSWORD)
                .role(role)
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

    @Test
    @DisplayName("register with a valid PASSENGER request saves a BCrypt hash and returns the profile")
    void register_validPassenger_returnsUserResponse() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();

        UserResponse response = authService.register(request("user@example.com", UserRole.PASSENGER));

        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertNotEquals(RAW_PASSWORD, saved.getPasswordHash());
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, saved.getPasswordHash()));
        assertEquals("Nguyen Van A", saved.getFullName());

        assertEquals(saved.getUserId(), response.getId());
        assertEquals("Nguyen Van A", response.getFullName());
        assertEquals("user@example.com", response.getEmail());
        assertEquals(UserRole.PASSENGER, response.getRole());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    @DisplayName("register with role DRIVER creates a DRIVER account")
    void register_validDriver_returnsDriverRole() {
        when(userRepository.existsByEmail("driver@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();

        UserResponse response = authService.register(request("driver@example.com", UserRole.DRIVER));

        assertEquals(UserRole.DRIVER, response.getRole());
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals(UserRole.DRIVER, userCaptor.getValue().getRole());
    }

    @Test
    @DisplayName("register trims and lower-cases the email before the uniqueness check and save")
    void register_emailWithUppercaseAndSpaces_storesNormalizedEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        stubSaveAndFlushAssignsId();

        UserResponse response = authService.register(request("  User@Example.COM ", UserRole.PASSENGER));

        verify(userRepository).existsByEmail("user@example.com");
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
        assertEquals("user@example.com", response.getEmail());
    }

    @Test
    @DisplayName("register with an email that already exists throws 409 EMAIL_ALREADY_REGISTERED without saving")
    void register_existingEmail_throwsConflict() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> authService.register(request("user@example.com", UserRole.PASSENGER)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("register that loses a race on the unique email index throws 409 instead of 500")
    void register_concurrentDuplicateEmail_uniqueViolation_throwsConflict() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> authService.register(request("user@example.com", UserRole.PASSENGER)));

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verify(userMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("register with role ADMIN is rejected with 400 ROLE_NOT_ALLOWED and touches no data")
    void register_adminRole_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request("admin@example.com", UserRole.ADMIN)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("ROLE_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(userRepository);
        verify(passwordEncoder, never()).encode(any());
    }
}
