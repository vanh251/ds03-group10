package com.example.pricingservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing estimated route metrics and upfront quote breakdown")
public class QuoteResponse {

    @Schema(description = "Estimated trip distance in kilometers", example = "2.5")
    private Double estimatedDistanceKm;

    @Schema(description = "Estimated trip duration in minutes", example = "12")
    private Integer estimatedDurationMin;

    @Schema(description = "Base fare amount", example = "15000")
    private BigDecimal baseFare;

    @Schema(description = "Calculated distance component of fare", example = "12500")
    private BigDecimal distanceFare;

    @Schema(description = "Calculated time component of fare", example = "6000")
    private BigDecimal timeFare;

    @Schema(description = "Estimated total fare", example = "33500")
    private BigDecimal totalFare;

    @Schema(description = "Currency code", example = "VND")
    private String currency;
}
