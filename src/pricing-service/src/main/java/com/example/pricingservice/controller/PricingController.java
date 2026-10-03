package com.example.pricingservice.controller;

import com.example.pricingservice.dto.request.FinalFareRequest;
import com.example.pricingservice.dto.request.QuoteRequest;
import com.example.pricingservice.dto.response.ErrorResponse;
import com.example.pricingservice.dto.response.FinalFareResponse;
import com.example.pricingservice.dto.response.QuoteResponse;
import com.example.pricingservice.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Ride fare estimation and final calculation APIs")
@SecurityRequirement(name = "bearerAuth")
public class PricingController {

    private final PricingService pricingService;

    @Operation(summary = "Calculate quote", description = "Calculates an upfront estimated fare for a ride based on distance and duration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote calculated successfully",
                    content = @Content(schema = @Schema(implementation = QuoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> getQuote(@Valid @RequestBody QuoteRequest request) {
        QuoteResponse response = pricingService.calculateQuote(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Calculate final fare", description = "Calculates the final fare for a completed trip based on actual distance and duration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Final fare calculated successfully",
                    content = @Content(schema = @Schema(implementation = FinalFareResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/final")
    public ResponseEntity<FinalFareResponse> getFinalFare(@Valid @RequestBody FinalFareRequest request) {
        FinalFareResponse response = pricingService.calculateFinalFare(request);
        return ResponseEntity.ok(response);
    }
}

