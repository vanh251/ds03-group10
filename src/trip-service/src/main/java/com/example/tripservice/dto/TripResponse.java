package com.example.tripservice.dto;

import com.example.tripservice.entity.TripStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripResponse {

    private UUID tripId;
    private TripStatus status;
    private UUID passengerId;
    private UUID driverId;
    private LocationDto pickup;
    private LocationDto destination;
    private BigDecimal estimatedFare;
    private BigDecimal finalFare;
    private String correlationId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
}
