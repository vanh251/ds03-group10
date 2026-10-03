package com.example.matchingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReserveDriverResponse {
    private UUID driverId;
    private String status;
    private UUID reservedForTrip;
    private Instant reservedUntil;
    private Integer version;
}
