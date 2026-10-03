package com.example.paymentservice.service;

import com.example.paymentservice.dto.request.PaymentChargeRequest;
import com.example.paymentservice.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse charge(String tripId, PaymentChargeRequest request, String idempotencyKeyHeader, String correlationId);

    PaymentResponse getPaymentByTripId(String tripId);

    PaymentResponse refund(String tripId, String correlationId);

    void processTripCompleted(String eventId, String tripId, String passengerId, Double finalFare, String currency, String correlationId);
}
