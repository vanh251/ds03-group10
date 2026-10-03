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
@Schema(description = "Response containing final trip fare and itemized breakdown")
public class FinalFareResponse {

    @Schema(description = "Trip ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String tripId;

    @Schema(description = "Base fare amount", example = "12000.0")
    private Double baseFare;

    @Schema(description = "Calculated distance component of fare", example = "32500.0")
    private Double distanceFare;

    @Schema(description = "Calculated time component of fare", example = "10000.0")
    private Double timeFare;

    @Schema(description = "Total final fare to charge", example = "54500.0")
    private Double totalFare;

    @Schema(description = "Currency code", example = "VND")
    private String currency;
}

