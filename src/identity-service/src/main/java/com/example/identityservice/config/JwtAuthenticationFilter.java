package com.example.identityservice.config;

import com.example.identityservice.entity.UserRole;
import com.example.identityservice.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates requests that carry {@code Authorization: Bearer <jwt>}.
 * A valid token puts the userId (principal) and ROLE_&lt;role&gt; authority in the SecurityContext.
 * An invalid or expired token is not rejected here: the reason is stored in the request attribute
 * {@link #JWT_ERROR_ATTRIBUTE} and the request continues unauthenticated, so public endpoints still work
 * and protected ones get a 401 from {@link JwtAuthenticationEntryPoint}.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseToken(header.substring(BEARER_PREFIX.length()).trim());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(toAuthentication(claims));
            SecurityContextHolder.setContext(context);
        } catch (ExpiredJwtException ex) {
            log.warn("Rejected expired JWT on path {}", request.getRequestURI());
            request.setAttribute(JWT_ERROR_ATTRIBUTE, TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Rejected invalid JWT on path {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
            request.setAttribute(JWT_ERROR_ATTRIBUTE, INVALID_TOKEN);
        }

        filterChain.doFilter(request, response);
    }

    // IllegalArgumentException from UUID.fromString / UserRole.valueOf means the claims are not ours
    private UsernamePasswordAuthenticationToken toAuthentication(Claims claims) {
        String subject = claims.getSubject();
        String role = claims.get(JwtService.ROLE_CLAIM, String.class);
        if (subject == null || role == null) {
            throw new MalformedJwtException("JWT is missing the sub or role claim");
        }
        UUID userId = UUID.fromString(subject);
        UserRole userRole = UserRole.valueOf(role);
        return UsernamePasswordAuthenticationToken.authenticated(userId, null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + userRole.name())));
    }
}
