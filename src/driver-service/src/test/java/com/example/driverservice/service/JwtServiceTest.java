package com.example.driverservice.service;

import com.example.driverservice.TestTokens;
import com.example.driverservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new JwtProperties(TestTokens.TEST_SECRET));

    @Test
    @DisplayName("parseToken reads sub and role from a token signed with the shared secret")
    void parseToken_validToken_returnsClaims() {
        UUID userId = UUID.randomUUID();

        Claims claims = jwtService.parseToken(TestTokens.token(userId, "DRIVER"));

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("DRIVER", claims.get(JwtService.ROLE_CLAIM, String.class));
    }

    @Test
    @DisplayName("parseToken with an expired token throws ExpiredJwtException")
    void parseToken_expiredToken_throwsExpired() {
        String token = TestTokens.token(TestTokens.TEST_SECRET, UUID.randomUUID().toString(), "DRIVER",
                Instant.now().minusSeconds(10));

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseToken(token));
    }

    @Test
    @DisplayName("parseToken with a token signed by another key throws SignatureException")
    void parseToken_otherKey_throwsSignatureException() {
        String token = TestTokens.token(TestTokens.OTHER_SECRET, UUID.randomUUID().toString(), "DRIVER",
                Instant.now().plusSeconds(60));

        assertThrows(SignatureException.class, () -> jwtService.parseToken(token));
    }

    @Test
    @DisplayName("parseToken with a value that is not a JWT throws JwtException")
    void parseToken_garbage_throwsJwtException() {
        assertThrows(JwtException.class, () -> jwtService.parseToken("not-a-jwt"));
    }
}
