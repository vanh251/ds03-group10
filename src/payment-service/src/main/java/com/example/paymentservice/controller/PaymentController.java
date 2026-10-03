package com.example.paymentservice.controller;

import com.example.paymentservice.dto.request.PaymentChargeRequest;
import com.example.paymentservice.dto.response.ErrorResponse;
import com.example.paymentservice.dto.response.PaymentResponse;
import com.example.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment charging, retrieval, and refund APIs")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Charge payment", description = "Processes a payment charge for a specified trip")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment charged successfully",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or simulated failure",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Payment conflict or duplicate idempotency key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{tripId}/charge")
    public ResponseEntity<PaymentResponse> chargePayment(
            @Parameter(description = "ID of the trip being charged", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String tripId,
            @Parameter(description = "Optional idempotency key to prevent duplicate charges")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Parameter(description = "Optional correlation ID for distributed tracing")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody PaymentChargeRequest request) {

        PaymentResponse response = paymentService.charge(tripId, request, idempotencyKeyHeader, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get payment details", description = "Retrieves payment record for a given trip ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found for given trip",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{tripId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "ID of the trip", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String tripId) {
        PaymentResponse response = paymentService.getPaymentByTripId(tripId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refund payment", description = "Refunds a completed payment for a specified trip")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment refunded successfully",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payment cannot be refunded in its current state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found for given trip",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{tripId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @Parameter(description = "ID of the trip to refund", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String tripId,
            @Parameter(description = "Optional correlation ID for distributed tracing")
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        PaymentResponse response = paymentService.refund(tripId, correlationId);
        return ResponseEntity.ok(response);
    }
}

