package com.example.identityservice.config;

import com.example.identityservice.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes the standard error body (CODING_CONVENTION section 16) with 401 when a protected endpoint
 * is called without a valid JWT. The code tells the client why: MISSING_TOKEN, INVALID_TOKEN or TOKEN_EXPIRED.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String MISSING_TOKEN = "MISSING_TOKEN";

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object jwtError = request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE);
        String code = jwtError != null ? jwtError.toString() : MISSING_TOKEN;

        Object correlationId = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_HEADER);
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(messageFor(code))
                .path(request.getRequestURI())
                .correlationId(correlationId != null ? correlationId.toString() : null)
                .code(code)
                .build();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

    private String messageFor(String code) {
        return switch (code) {
            case JwtAuthenticationFilter.TOKEN_EXPIRED -> "JWT has expired, please log in again";
            case JwtAuthenticationFilter.INVALID_TOKEN -> "JWT is invalid";
            default -> "Authentication required: send 'Authorization: Bearer <token>'";
        };
    }
}
