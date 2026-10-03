package com.example.pricingservice.dto.request;

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
public class FinalFareRequest {

    @NotBlank(message = "Trip ID is required")
    private String tripId;

    @NotNull(message = "Actual distance in km is required")
    @DecimalMin(value = "0.0", message = "Actual distance must be non-negative")
    private Double actualDistanceKm;

    @NotNull(message = "Actual duration in minutes is required")
    @Min(value = 0, message = "Actual duration must be non-negative")
    private Integer actualDurationMin;
}
