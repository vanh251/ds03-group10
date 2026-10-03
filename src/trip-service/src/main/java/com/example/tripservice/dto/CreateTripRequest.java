package com.example.tripservice.dto;

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
public class CreateTripRequest {

    private UUID passengerId;

    @NotNull(message = "Pickup location is required")
    @Valid
    private LocationDto pickup;

    @NotNull(message = "Destination location is required")
    @Valid
    private LocationDto destination;

    private BigDecimal estimatedFare;
}
