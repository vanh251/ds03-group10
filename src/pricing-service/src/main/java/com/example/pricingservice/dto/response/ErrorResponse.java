package com.example.pricingservice.dto.response;

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

    @Schema(description = "Detailed error message", example = "Pickup location is required")
    private String message;

    @Schema(description = "Request path that caused error", example = "/api/pricing/quote")
    private String path;

    @Schema(description = "Correlation ID for request tracing", example = "c8f331f4-0012-4299-9b57-c37f0001")
    private String correlationId;

    @Schema(description = "Specific application error code", example = "VALIDATION_FAILED")
    private String code;
}

