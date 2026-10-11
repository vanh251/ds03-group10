package com.example.identityservice.service;

import com.example.identityservice.config.JwtProperties;
import com.example.identityservice.entity.User;
import com.example.identityservice.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = base64("unit-test-secret-key-for-jwt-service-at-least-32-bytes");
    private static final String OTHER_SECRET = base64("another-secret-key-that-did-not-sign-the-token-xyz");

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 3_600_000));

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static User user(UserRole role) {
        return User.builder()
                .userId(UUID.randomUUID())
                .email("driver01@ds03.test")
                .fullName("Tran Van Binh")
                .role(role)
                .build();
    }

    @Test
    @DisplayName("generateToken puts the userId in sub and the role in the role claim, valid for the configured lifetime")
    void generateToken_validUser_containsSubjectRoleAndExpiry() {
        User user = user(UserRole.DRIVER);

        Claims claims = jwtService.parseToken(jwtService.generateToken(user));

        assertEquals(user.getUserId().toString(), claims.getSubject());
        assertEquals("DRIVER", claims.get(JwtService.ROLE_CLAIM, String.class));
        long lifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(3_600_000, lifetimeMs);
    }

    @Test
    @DisplayName("generateToken signs with HS256 as documented in docs/api.md")
    void generateToken_validUser_usesHs256() {
        String token = jwtService.generateToken(user(UserRole.PASSENGER));

        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertTrue(header.contains("\"alg\":\"HS256\""), header);
    }

    @Test
    @DisplayName("getExpirationSeconds converts the configured milliseconds to seconds")
    void getExpirationSeconds_configuredMillis_returnsSeconds() {
        assertEquals(3600, jwtService.getExpirationSeconds());
    }

    @Test
    @DisplayName("parseToken with an expired token throws ExpiredJwtException")
    void parseToken_expiredToken_throwsExpiredJwtException() {
        JwtService alreadyExpired = new JwtService(new JwtProperties(SECRET, -60_000));
        String token = alreadyExpired.generateToken(user(UserRole.PASSENGER));

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseToken(token));
    }

    @Test
    @DisplayName("parseToken with a token signed by another key throws SignatureException")
    void parseToken_tokenSignedWithOtherKey_throwsSignatureException() {
        String token = new JwtService(new JwtProperties(OTHER_SECRET, 3_600_000)).generateToken(user(UserRole.DRIVER));

        assertThrows(SignatureException.class, () -> jwtService.parseToken(token));
    }

    @Test
    @DisplayName("parseToken with a modified payload (e.g. role escalated to ADMIN) is rejected")
    void parseToken_tamperedPayload_throwsJwtException() {
        String[] parts = jwtService.generateToken(user(UserRole.PASSENGER)).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"x\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> jwtService.parseToken(parts[0] + "." + forgedPayload + "." + parts[2]));
    }

    @Test
    @DisplayName("parseToken with an unsigned token (alg none) is rejected")
    void parseToken_unsignedToken_throwsJwtException() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"x\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));

        assertThrows(JwtException.class, () -> jwtService.parseToken(header + "." + payload + "."));
    }

    @Test
    @DisplayName("parseToken with a value that is not a JWT throws JwtException")
    void parseToken_garbage_throwsJwtException() {
        assertThrows(JwtException.class, () -> jwtService.parseToken("not-a-jwt"));
    }

    @Test
    @DisplayName("the constructor rejects a secret shorter than 256 bits")
    void constructor_weakSecret_throwsWeakKeyException() {
        JwtProperties weak = new JwtProperties(base64("too-short"), 3_600_000);

        assertThrows(WeakKeyException.class, () -> new JwtService(weak));
    }
}
