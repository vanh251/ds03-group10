package com.example.identityservice.service;

import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.ResourceNotFoundException;
import com.example.identityservice.mapper.UserMapper;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("getUserById with an existing id returns the public profile")
    void getUserById_existingUser_returnsUserResponse() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .userId(id)
                .email("driver01@ds03.test")
                .passwordHash("$2a$10$hash")
                .fullName("Tran Van Binh")
                .role(UserRole.DRIVER)
                .createdAt(Instant.parse("2026-10-08T10:00:00Z"))
                .build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(id);

        assertEquals(id, response.getId());
        assertEquals("Tran Van Binh", response.getFullName());
        assertEquals("driver01@ds03.test", response.getEmail());
        assertEquals(UserRole.DRIVER, response.getRole());
        assertEquals(Instant.parse("2026-10-08T10:00:00Z"), response.getCreatedAt());
    }

    @Test
    @DisplayName("getUserById with an unknown id throws 404 USER_NOT_FOUND")
    void getUserById_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(id));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("USER_NOT_FOUND", ex.getCode());
    }
}
