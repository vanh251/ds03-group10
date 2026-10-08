package com.example.identityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error response schema")
public class ErrorResponse {

    @Schema(description = "Timestamp when error occurred", example = "2026-10-03T10:00:00Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "409")
    private Integer status;

    @Schema(description = "HTTP error reason phrase", example = "Conflict")
    private String error;

    @Schema(description = "Detailed error message", example = "Email already registered")
    private String message;

    @Schema(description = "Request path that caused error", example = "/api/auth/register")
    private String path;

    @Schema(description = "Correlation ID for request tracing", example = "550e8400-e29b-41d4-a716-446655440000")
    private String correlationId;

    @Schema(description = "Specific application error code", example = "EMAIL_ALREADY_REGISTERED")
    private String code;
}
