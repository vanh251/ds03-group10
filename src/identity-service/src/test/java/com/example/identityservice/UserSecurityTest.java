package com.example.identityservice;

import com.example.identityservice.config.JwtProperties;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs GET /api/users/{id} through the real Spring Security chain and JwtAuthenticationFilter
 * (only the repository is mocked) to cover the JWT security cases from SRS section 23.4.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserSecurityTest {

    private static final String PASSWORD = "S3cure-Pass!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    private User storedUser(UserRole role) {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("driver01@ds03.test")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Tran Van Binh")
                .role(role)
                .createdAt(Instant.parse("2026-10-08T10:00:00Z"))
                .build();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    @DisplayName("Login then GET /api/users/{id} with the issued token returns 200 with the profile")
    void getUserById_tokenFromLogin_returns200() throws Exception {
        User driver = storedUser(UserRole.DRIVER);
        when(userRepository.findByEmail("driver01@ds03.test")).thenReturn(Optional.of(driver));
        when(userRepository.findById(driver.getUserId())).thenReturn(Optional.of(driver));

        String loginBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "driver01@ds03.test", "password": "S3cure-Pass!"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(loginBody, "$.accessToken");

        mockMvc.perform(get("/api/users/{id}", driver.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(driver.getUserId().toString()))
                .andExpect(jsonPath("$.fullName").value("Tran Van Binh"))
                .andExpect(jsonPath("$.email").value("driver01@ds03.test"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("A PASSENGER token may read another user's profile (any authenticated user, docs/api.md)")
    void getUserById_passengerTokenForOtherUser_returns200() throws Exception {
        User driver = storedUser(UserRole.DRIVER);
        User passenger = storedUser(UserRole.PASSENGER);
        when(userRepository.findById(driver.getUserId())).thenReturn(Optional.of(driver));

        mockMvc.perform(get("/api/users/{id}", driver.getUserId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(passenger))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(driver.getUserId().toString()));
    }

    @Test
    @DisplayName("GET /api/users/{id} with a valid token for an unknown id returns 404 USER_NOT_FOUND")
    void getUserById_validTokenUnknownId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{id}", unknownId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(storedUser(UserRole.PASSENGER)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/users/{id} without a JWT returns 401 MISSING_TOKEN with the standard error body")
    void getUserById_missingToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/users/{id}", id).header("X-Correlation-ID", "corr-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("MISSING_TOKEN"))
                .andExpect(jsonPath("$.path").value("/api/users/" + id))
                .andExpect(jsonPath("$.correlationId").value("corr-401"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("GET /api/users/{id} with a non-Bearer Authorization header returns 401 MISSING_TOKEN")
    void getUserById_basicAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_TOKEN"));
    }

    @Test
    @DisplayName("GET /api/users/{id} with a malformed JWT returns 401 INVALID_TOKEN")
    void getUserById_malformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("GET /api/users/{id} with an empty Bearer token returns 401 INVALID_TOKEN")
    void getUserById_emptyBearerToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("GET /api/users/{id} with an expired JWT returns 401 TOKEN_EXPIRED")
    void getUserById_expiredToken_returns401() throws Exception {
        JwtService expiredIssuer = new JwtService(new JwtProperties(jwtProperties.secret(), -60_000));

        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredIssuer.generateToken(storedUser(UserRole.DRIVER)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("GET /api/users/{id} with a JWT signed by another key returns 401 INVALID_TOKEN")
    void getUserById_tokenSignedWithOtherKey_returns401() throws Exception {
        String otherSecret = Base64.getEncoder().encodeToString(
                "another-secret-key-that-did-not-sign-the-token-xyz".getBytes(StandardCharsets.UTF_8));
        JwtService foreignIssuer = new JwtService(new JwtProperties(otherSecret, 60_000));

        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(foreignIssuer.generateToken(storedUser(UserRole.DRIVER)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("GET /api/users/{id} with a correctly signed JWT that lacks the role claim returns 401 INVALID_TOKEN")
    void getUserById_tokenWithoutRoleClaim_returns401() throws Exception {
        String token = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())), SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("A bad Bearer token does not block public endpoints: POST /api/auth/login still works")
    void login_withInvalidBearerHeader_isStillPublic() throws Exception {
        User driver = storedUser(UserRole.DRIVER);
        when(userRepository.findByEmail("driver01@ds03.test")).thenReturn(Optional.of(driver));

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "driver01@ds03.test", "password": "S3cure-Pass!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
}
