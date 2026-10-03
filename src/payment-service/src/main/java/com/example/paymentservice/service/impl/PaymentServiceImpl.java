package com.example.paymentservice.service.impl;

import com.example.paymentservice.dto.request.PaymentChargeRequest;
import com.example.paymentservice.dto.response.PaymentResponse;
import com.example.paymentservice.entity.OutboxEvent;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.entity.ProcessedEvent;
import com.example.paymentservice.exception.BusinessException;
import com.example.paymentservice.exception.ConflictException;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.kafka.producer.PaymentEventProducer;
import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedEventRepository;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PaymentResponse charge(String tripId, PaymentChargeRequest request, String idempotencyKeyHeader, String correlationId) {
        String idempotencyKey = resolveIdempotencyKey(request, idempotencyKeyHeader, tripId);
        String resolvedCorrelationId = (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();

        log.info("Processing charge for tripId={}, idempotencyKey={}, correlationId={}",
                tripId, idempotencyKey, resolvedCorrelationId);

        // Idempotency check: prevent duplicate charges
        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPaymentOpt.isPresent()) {
            Payment existing = existingPaymentOpt.get();
            log.info("Duplicate idempotency key '{}' detected for trip {}. Returning existing payment {}",
                    idempotencyKey, tripId, existing.getPaymentId());

            if (!existing.getTripId().equals(tripId) || !existing.getAmount().equals(request.getAmount())) {
                throw new ConflictException("Idempotency key already used for a different transaction", "IDEMPOTENCY_CONFLICT");
            }
            return mapToResponse(existing);
        }

        String paymentId = "pay-" + UUID.randomUUID().toString().substring(0, 8);
        boolean isSimulatedFailure = Boolean.TRUE.equals(request.getSimulateFailure())
                || (request.getPassengerId() != null && request.getPassengerId().contains("fail"))
                || tripId.contains("fail");

        if (isSimulatedFailure) {
            log.warn("Simulated payment failure triggered for tripId={}, paymentId={}", tripId, paymentId);
            Payment failedPayment = Payment.builder()
                    .paymentId(paymentId)
                    .tripId(tripId)
                    .passengerId(request.getPassengerId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                    .status(PaymentStatus.FAILED)
                    .failureReason("SIMULATED_FAILURE")
                    .correlationId(resolvedCorrelationId)
                    .idempotencyKey(idempotencyKey)
                    .build();

            paymentRepository.save(failedPayment);
            saveOutboxEvent("PaymentFailed", paymentId, tripId, failedPayment, resolvedCorrelationId);
            paymentEventProducer.publishPaymentFailed(paymentId, tripId, request.getPassengerId(),
                    request.getAmount(), request.getCurrency(), "SIMULATED_FAILURE", resolvedCorrelationId, null);

            throw new BusinessException("Payment charge failed: simulated payment failure",
                    HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED");
        }

        Instant now = Instant.now();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .tripId(tripId)
                .passengerId(request.getPassengerId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod("MOCK_CARD")
                .gatewayRef("GW-" + UUID.randomUUID().toString().substring(0, 8))
                .correlationId(resolvedCorrelationId)
                .idempotencyKey(idempotencyKey)
                .capturedAt(now)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment {} charged successfully for tripId={}, amount={}", paymentId, tripId, request.getAmount());

        saveOutboxEvent("PaymentSucceeded", paymentId, tripId, savedPayment, resolvedCorrelationId);
        paymentEventProducer.publishPaymentSucceeded(paymentId, tripId, request.getPassengerId(),
                request.getAmount(), request.getCurrency(), resolvedCorrelationId, null);

        return mapToResponse(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTripId(String tripId) {
        Payment payment = paymentRepository.findTopByTripIdOrderByCreatedAtDesc(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for trip " + tripId));
        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refund(String tripId, String correlationId) {
        String resolvedCorrelationId = (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();
        log.info("Processing refund for tripId={}, correlationId={}", tripId, resolvedCorrelationId);

        Payment payment = paymentRepository.findTopByTripIdOrderByCreatedAtDesc(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for trip " + tripId));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment {} for trip {} is already refunded", payment.getPaymentId(), tripId);
            return mapToResponse(payment);
        }

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Cannot refund payment with status: " + payment.getStatus(), "INVALID_REFUND_STATE");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());
        Payment saved = paymentRepository.save(payment);
        log.info("Payment {} for trip {} refunded successfully", saved.getPaymentId(), tripId);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void processTripCompleted(String eventId, String tripId, String passengerId,
                                     Double finalFare, String currency, String correlationId) {
        log.info("Processing TripCompleted event: eventId={}, tripId={}, fare={}", eventId, tripId, finalFare);

        // Deduplication check: Section 21.1 CODING_CONVENTION
        if (processedEventRepository.existsByEventId(eventId)) {
            log.warn("Duplicate event detected, skipping eventId={}", eventId);
            return;
        }

        String idempotencyKey = "trip-" + tripId + "-completed";
        String paymentId = "pay-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .tripId(tripId)
                .passengerId(passengerId)
                .amount(finalFare)
                .currency(currency != null ? currency : "VND")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod("MOCK_CARD")
                .gatewayRef("GW-AUTO-" + UUID.randomUUID().toString().substring(0, 8))
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .capturedAt(now)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType("TripCompleted")
                .processedAt(now)
                .build();
        processedEventRepository.save(processedEvent);

        saveOutboxEvent("PaymentSucceeded", paymentId, tripId, savedPayment, correlationId);
        paymentEventProducer.publishPaymentSucceeded(paymentId, tripId, passengerId, finalFare, currency, correlationId, eventId);

        log.info("TripCompleted processed successfully for tripId={}, paymentId={}", tripId, paymentId);
    }

    private String resolveIdempotencyKey(PaymentChargeRequest request, String headerKey, String tripId) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey;
        }
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            return request.getIdempotencyKey();
        }
        return "charge-" + tripId + "-" + UUID.randomUUID();
    }

    private void saveOutboxEvent(String eventType, String paymentId, String tripId, Object payload, String correlationId) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .aggregateType("Payment")
                    .aggregateId(paymentId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .correlationId(correlationId)
                    .published(true)
                    .publishedAt(Instant.now())
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to record outbox event for paymentId={}", paymentId, e);
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .tripId(payment.getTripId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .processedAt(payment.getCapturedAt() != null ? payment.getCapturedAt() : payment.getCreatedAt())
                .refundedAt(payment.getRefundedAt())
                .failureReason(payment.getFailureReason())
                .build();
    }
}
