package com.example.paymentservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to initiate a payment charge for a completed ride")
public class PaymentChargeRequest {

    @Schema(description = "ID of the passenger paying for the ride", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "Passenger ID is required")
    private UUID passengerId;

    @Schema(description = "Total payment amount to charge", example = "36500")
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Amount must have at most 8 integer digits and 2 decimals")
    private BigDecimal amount;

    @Schema(description = "Payment currency code (ISO 4217)", example = "VND")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code")
    @Builder.Default
    private String currency = "VND";

    @Schema(description = "Idempotency key to prevent duplicate charges; the X-Idempotency-Key header takes precedence",
            example = "trip-001-charge-001")
    @Size(max = 255, message = "Idempotency key must be at most 255 characters")
    private String idempotencyKey;
}
