package com.example.identityservice.controller;

import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.exception.GlobalExceptionHandler;
import com.example.identityservice.exception.ResourceNotFoundException;
import com.example.identityservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/users/{id} with an existing id returns 200 with the profile and no password fields")
    void getUserById_existingUser_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id)).thenReturn(UserResponse.builder()
                .id(id)
                .fullName("Tran Van Binh")
                .email("driver01@ds03.test")
                .role(UserRole.DRIVER)
                .createdAt(Instant.parse("2026-10-08T10:00:00Z"))
                .build());

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.fullName").value("Tran Van Binh"))
                .andExpect(jsonPath("$.email").value("driver01@ds03.test"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/{id} with an unknown id returns 404 USER_NOT_FOUND")
    void getUserById_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id)).thenThrow(new ResourceNotFoundException("User not found", "USER_NOT_FOUND"));

        mockMvc.perform(get("/api/users/{id}", id).header("X-Correlation-ID", "corr-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/users/" + id))
                .andExpect(jsonPath("$.correlationId").value("corr-404"));
    }

    @Test
    @DisplayName("GET /api/users/{id} with an id that is not a UUID returns 400 INVALID_PARAMETER")
    void getUserById_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));

        verifyNoInteractions(userService);
    }
}
