package com.example.pricingservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for calculating the final trip fare upon trip completion")
public class FinalFareRequest {

    @Schema(description = "ID of the completed trip", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "Trip ID is required")
    private String tripId;

    @Schema(description = "Actual traveled distance in kilometers", example = "6.5")
    @NotNull(message = "Actual distance in km is required")
    @DecimalMin(value = "0.0", message = "Actual distance must be non-negative")
    private Double actualDistanceKm;

    @Schema(description = "Actual trip duration in minutes", example = "20")
    @NotNull(message = "Actual duration in minutes is required")
    @Min(value = 0, message = "Actual duration must be non-negative")
    private Integer actualDurationMin;
}

