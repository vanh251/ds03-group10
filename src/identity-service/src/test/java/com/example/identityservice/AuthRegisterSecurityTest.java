package com.example.identityservice;

import com.example.identityservice.entity.User;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs register through the real filter chain (Spring Security + CorrelationIdFilter + validation)
 * with only the repository mocked, to prove the endpoint is public and wired end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthRegisterSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/auth/register without an Authorization header is allowed and returns 201")
    void register_withoutJwt_returns201() throws Exception {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(UUID.randomUUID());
            user.setCreatedAt(Instant.now());
            return user;
        });

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Nguyen Van A",
                                  "email": "user@example.com",
                                  "password": "S3cure-Pass!",
                                  "role": "PASSENGER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @DisplayName("The correlationId sent by the gateway is propagated into the error body")
    void register_invalidBodyWithCorrelationHeader_returns400WithSameCorrelationId() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", correlationId)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }
}
