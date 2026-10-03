package com.example.paymentservice.controller;

import com.example.paymentservice.dto.request.PaymentChargeRequest;
import com.example.paymentservice.dto.response.PaymentResponse;
import com.example.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{tripId}/charge")
    public ResponseEntity<PaymentResponse> chargePayment(
            @PathVariable String tripId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody PaymentChargeRequest request) {

        PaymentResponse response = paymentService.charge(tripId, request, idempotencyKeyHeader, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String tripId) {
        PaymentResponse response = paymentService.getPaymentByTripId(tripId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{tripId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable String tripId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        PaymentResponse response = paymentService.refund(tripId, correlationId);
        return ResponseEntity.ok(response);
    }
}
