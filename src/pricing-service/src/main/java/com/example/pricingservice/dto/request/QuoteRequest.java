package com.example.pricingservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Request body of POST /api/pricing/quote (docs/api.md §6): distance and duration are always estimated
// by the Pricing Service, so a client cannot lower its own quote by sending them.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for upfront trip quote calculation")
public class QuoteRequest {

    @Schema(description = "Trip pickup location")
    @Valid
    @NotNull(message = "Pickup location is required")
    private LocationDto pickup;

    @Schema(description = "Trip destination location")
    @Valid
    @NotNull(message = "Destination location is required")
    private LocationDto destination;
}
