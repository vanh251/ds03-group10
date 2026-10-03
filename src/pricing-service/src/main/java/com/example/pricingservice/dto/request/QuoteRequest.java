package com.example.pricingservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {

    @Valid
    @NotNull(message = "Pickup location is required")
    private LocationDto pickup;

    @Valid
    @NotNull(message = "Destination location is required")
    private LocationDto destination;

    private Double distanceKm;

    private Integer durationMin;
}
