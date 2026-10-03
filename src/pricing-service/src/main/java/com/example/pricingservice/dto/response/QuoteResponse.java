package com.example.pricingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {

    private Double estimatedDistanceKm;
    private Integer estimatedDurationMin;
    private Double baseFare;
    private Double distanceFare;
    private Double timeFare;
    private Double totalFare;
    private String currency;
}
