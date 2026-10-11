package com.example.identityservice;

import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs login through the real filter chain, BCrypt encoder and JwtService with only the repository mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthLoginSecurityTest {

    private static final String PASSWORD = "S3cure-Pass!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User storedDriver() {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("driver01@ds03.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Tran Van Binh")
                .role(UserRole.DRIVER)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/login without an Authorization header returns 200 and a token the service can verify")
    void login_withoutJwt_returns200WithVerifiableToken() throws Exception {
        User driver = storedDriver();
        when(userRepository.findByEmail("driver01@ds03.test")).thenReturn(Optional.of(driver));

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "driver01@ds03.test", "password": "S3cure-Pass!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.userId").value(driver.getUserId().toString()))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andReturn().getResponse().getContentAsString();

        Claims claims = jwtService.parseToken(JsonPath.read(body, "$.accessToken"));
        assertEquals(driver.getUserId().toString(), claims.getSubject());
        assertEquals("DRIVER", claims.get(JwtService.ROLE_CLAIM, String.class));
    }

    @Test
    @DisplayName("POST /api/auth/login with a wrong password returns 401 INVALID_CREDENTIALS")
    void login_wrongPassword_returns401() throws Exception {
        when(userRepository.findByEmail("driver01@ds03.test")).thenReturn(Optional.of(storedDriver()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "driver01@ds03.test", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
}
