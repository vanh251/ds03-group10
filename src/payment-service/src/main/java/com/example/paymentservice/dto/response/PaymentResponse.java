package com.example.paymentservice.dto.response;

import com.example.paymentservice.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response containing payment transaction details")
public class PaymentResponse {

    @Schema(description = "Payment transaction ID", example = "550e8400-e29b-41d4-a716-446655440099")
    private String paymentId;

    @Schema(description = "Trip ID associated with payment", example = "550e8400-e29b-41d4-a716-446655440000")
    private String tripId;

    @Schema(description = "Current payment status", example = "SUCCESS")
    private PaymentStatus status;

    @Schema(description = "Payment transaction amount", example = "54500.0")
    private Double amount;

    @Schema(description = "Currency code", example = "VND")
    private String currency;

    @Schema(description = "Timestamp when payment was processed", example = "2026-10-03T10:00:00Z")
    private Instant processedAt;

    @Schema(description = "Timestamp when payment was refunded (if applicable)", example = "2026-10-03T10:05:00Z")
    private Instant refundedAt;

    @Schema(description = "Failure reason if status is FAILED", example = "INSUFFICIENT_FUNDS")
    private String failureReason;
}

