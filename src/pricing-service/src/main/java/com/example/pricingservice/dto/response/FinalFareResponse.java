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
@Schema(description = "Response containing final trip fare and itemized breakdown")
public class FinalFareResponse {

    @Schema(description = "Trip ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String tripId;

    @Schema(description = "Base fare amount", example = "15000")
    private BigDecimal baseFare;

    @Schema(description = "Calculated distance component of fare", example = "14000")
    private BigDecimal distanceFare;

    @Schema(description = "Calculated time component of fare", example = "7500")
    private BigDecimal timeFare;

    @Schema(description = "Total final fare to charge", example = "36500")
    private BigDecimal totalFare;

    @Schema(description = "Currency code", example = "VND")
    private String currency;
}
