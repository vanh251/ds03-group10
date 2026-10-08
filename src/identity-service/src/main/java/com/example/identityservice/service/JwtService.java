package com.example.identityservice.service;

import com.example.identityservice.config.JwtProperties;
import com.example.identityservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies HS256 access tokens. The subject is the userId and the {@value #ROLE_CLAIM} claim holds the UserRole.
 */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    public static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long expirationMs;
    private final JwtParser parser;

    public JwtService(JwtProperties properties) {
        // Fails fast at startup if the secret is not Base64 or shorter than 256 bits
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.expirationMs = properties.expirationMs();
        this.parser = Jwts.parserBuilder().setSigningKey(signingKey).build();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(user.getUserId().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Verifies the signature and expiry and returns the claims.
     * Throws {@link io.jsonwebtoken.ExpiredJwtException} for an expired token and another
     * {@link io.jsonwebtoken.JwtException} (or IllegalArgumentException for a blank token) for any other invalid token.
     */
    public Claims parseToken(String token) {
        return parser.parseClaimsJws(token).getBody();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
