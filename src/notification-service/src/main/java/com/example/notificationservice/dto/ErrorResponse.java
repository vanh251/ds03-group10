package com.example.notificationservice.dto;

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

    @Schema(description = "HTTP status code", example = "400")
    private Integer status;

    @Schema(description = "HTTP error reason phrase", example = "Bad Request")
    private String error;

    @Schema(description = "Detailed error message", example = "Recipient ID is required")
    private String message;

    @Schema(description = "Request path that caused error", example = "/api/notifications/send")
    private String path;

    @Schema(description = "Correlation ID for request tracing", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private String correlationId;

    @Schema(description = "Specific application error code", example = "VALIDATION_ERROR")
    private String code;
}
