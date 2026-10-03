package com.example.pricingservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing estimated route metrics and upfront quote breakdown")
public class QuoteResponse {

    @Schema(description = "Estimated trip distance in kilometers", example = "5.2")
    private Double estimatedDistanceKm;

    @Schema(description = "Estimated trip duration in minutes", example = "15")
    private Integer estimatedDurationMin;

    @Schema(description = "Base fare amount", example = "12000.0")
    private Double baseFare;

    @Schema(description = "Calculated distance component of fare", example = "26000.0")
    private Double distanceFare;

    @Schema(description = "Calculated time component of fare", example = "7500.0")
    private Double timeFare;

    @Schema(description = "Estimated total fare", example = "45500.0")
    private Double totalFare;

    @Schema(description = "Currency code", example = "VND")
    private String currency;
}

