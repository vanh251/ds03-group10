package com.example.tripservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body to create a new ride request")
public class CreateTripRequest {

    @Schema(description = "Optional passenger UUID (defaults to authenticated user)", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID passengerId;

    @NotNull(message = "Pickup location is required")
    @Valid
    @Schema(description = "Pickup location with coordinates and address", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocationDto pickup;

    @NotNull(message = "Destination location is required")
    @Valid
    @Schema(description = "Destination location with coordinates and address", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocationDto destination;

    @Schema(description = "Estimated fare calculated or quoted", example = "45000")
    private BigDecimal estimatedFare;
}
