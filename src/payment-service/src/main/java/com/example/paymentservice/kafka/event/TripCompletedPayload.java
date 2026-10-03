package com.example.paymentservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCompletedPayload {

    private String tripId;
    private String driverId;
    private String passengerId;
    private Double actualDistanceKm;
    private Integer actualDurationMin;
    private Double finalFare;
    private String currency;
    private Instant completedAt;
}
