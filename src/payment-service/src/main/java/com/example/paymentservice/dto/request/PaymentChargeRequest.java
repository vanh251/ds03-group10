package com.example.paymentservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to initiate a payment charge for a completed ride")
public class PaymentChargeRequest {

    @Schema(description = "ID of the passenger paying for the ride", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "Passenger ID is required")
    private String passengerId;

    @Schema(description = "Total payment amount to charge", example = "54500.0")
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private Double amount;

    @Schema(description = "Payment currency code", example = "VND")
    @Builder.Default
    private String currency = "VND";

    @Schema(description = "Client-supplied idempotency key to prevent duplicate charges", example = "idem-trip-12345")
    private String idempotencyKey;

    @Schema(description = "Flag for simulated test failures in sandbox/dev environments", example = "false")
    @Builder.Default
    private Boolean simulateFailure = false;
}

