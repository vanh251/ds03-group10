package com.example.paymentservice;

import com.example.paymentservice.dto.request.PaymentChargeRequest;
import com.example.paymentservice.dto.response.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.BusinessException;
import com.example.paymentservice.exception.ConflictException;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.kafka.producer.PaymentEventProducer;
import com.example.paymentservice.repository.OutboxEventRepository;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.repository.ProcessedEventRepository;
import com.example.paymentservice.service.PaymentService;
import com.example.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                processedEventRepository,
                outboxEventRepository,
                paymentEventProducer,
                new JsonMapper()
        );
    }

    @Test
    @DisplayName("charge with valid request creates successful payment and publishes event")
    void charge_validRequest_createsSuccessfulPayment() {
        PaymentChargeRequest request = PaymentChargeRequest.builder()
                .passengerId("passenger-001")
                .amount(36500.0)
                .currency("VND")
                .idempotencyKey("idem-001")
                .build();

        when(paymentRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.charge("trip-001", request, "idem-001", "corr-001");

        assertNotNull(response);
        assertEquals("trip-001", response.getTripId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(36500.0, response.getAmount());
        assertEquals("VND", response.getCurrency());
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentEventProducer).publishPaymentSucceeded(any(), eq("trip-001"), eq("passenger-001"), eq(36500.0), eq("VND"), any(), any());
    }

    @Test
    @DisplayName("charge with duplicate idempotency key returns existing payment without re-charging")
    void charge_duplicateIdempotencyKey_returnsExistingPayment() {
        Payment existingPayment = Payment.builder()
                .paymentId("pay-existing")
                .tripId("trip-001")
                .passengerId("passenger-001")
                .amount(36500.0)
                .currency("VND")
                .status(PaymentStatus.SUCCESS)
                .idempotencyKey("idem-001")
                .createdAt(Instant.now())
                .capturedAt(Instant.now())
                .build();

        PaymentChargeRequest request = PaymentChargeRequest.builder()
                .passengerId("passenger-001")
                .amount(36500.0)
                .currency("VND")
                .idempotencyKey("idem-001")
                .build();

        when(paymentRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.charge("trip-001", request, "idem-001", "corr-001");

        assertNotNull(response);
        assertEquals("pay-existing", response.getPaymentId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("charge with duplicate key but different parameters throws ConflictException")
    void charge_duplicateKeyDifferentParams_throwsConflictException() {
        Payment existingPayment = Payment.builder()
                .paymentId("pay-existing")
                .tripId("trip-001")
                .passengerId("passenger-001")
                .amount(50000.0)
                .idempotencyKey("idem-001")
                .build();

        PaymentChargeRequest request = PaymentChargeRequest.builder()
                .passengerId("passenger-001")
                .amount(36500.0)
                .currency("VND")
                .idempotencyKey("idem-001")
                .build();

        when(paymentRepository.findByIdempotencyKey("idem-001")).thenReturn(Optional.of(existingPayment));

        assertThrows(ConflictException.class, () ->
                paymentService.charge("trip-001", request, "idem-001", "corr-001"));
    }

    @Test
    @DisplayName("charge with simulated failure saves failed payment and throws BusinessException")
    void charge_simulatedFailure_savesFailedPaymentAndThrowsException() {
        PaymentChargeRequest request = PaymentChargeRequest.builder()
                .passengerId("passenger-001")
                .amount(36500.0)
                .currency("VND")
                .idempotencyKey("idem-fail-001")
                .simulateFailure(true)
                .build();

        when(paymentRepository.findByIdempotencyKey("idem-fail-001")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(BusinessException.class, () ->
                paymentService.charge("trip-001", request, "idem-fail-001", "corr-001"));

        verify(paymentRepository).save(any(Payment.class));
        verify(paymentEventProducer).publishPaymentFailed(any(), eq("trip-001"), eq("passenger-001"), eq(36500.0), eq("VND"), any(), any(), any());
    }

    @Test
    @DisplayName("refund with existing successful payment updates status to REFUNDED")
    void refund_successfulPayment_updatesStatusToRefunded() {
        Payment payment = Payment.builder()
                .paymentId("pay-001")
                .tripId("trip-001")
                .passengerId("passenger-001")
                .amount(36500.0)
                .status(PaymentStatus.SUCCESS)
                .createdAt(Instant.now())
                .capturedAt(Instant.now())
                .build();

        when(paymentRepository.findTopByTripIdOrderByCreatedAtDesc("trip-001")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.refund("trip-001", "corr-001");

        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertNotNull(response.getRefundedAt());
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("refund on non-existing trip payment throws ResourceNotFoundException")
    void refund_nonExistingPayment_throwsNotFound() {
        when(paymentRepository.findTopByTripIdOrderByCreatedAtDesc("trip-unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                paymentService.refund("trip-unknown", "corr-001"));
    }

    @Test
    @DisplayName("processTripCompleted with duplicate event does not process twice (idempotency)")
    void processTripCompleted_duplicateEvent_doesNotProcessTwice() {
        when(processedEventRepository.existsByEventId("evt-dup")).thenReturn(true);

        paymentService.processTripCompleted("evt-dup", "trip-001", "passenger-001", 36500.0, "VND", "corr-001");

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentEventProducer, never()).publishPaymentSucceeded(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("processTripCompleted with new event processes charge and marks processed")
    void processTripCompleted_newEvent_processesCharge() {
        when(processedEventRepository.existsByEventId("evt-new")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.processTripCompleted("evt-new", "trip-001", "passenger-001", 36500.0, "VND", "corr-001");

        verify(paymentRepository).save(any(Payment.class));
        verify(processedEventRepository).save(any());
        verify(paymentEventProducer).publishPaymentSucceeded(any(), eq("trip-001"), eq("passenger-001"), eq(36500.0), eq("VND"), any(), eq("evt-new"));
    }
}
