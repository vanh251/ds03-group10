package com.example.paymentservice.kafka.producer;

import com.example.paymentservice.entity.OutboxEvent;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.kafka.event.EventEnvelope;
import com.example.paymentservice.kafka.event.PaymentFailedPayload;
import com.example.paymentservice.kafka.event.PaymentSucceededPayload;
import com.example.paymentservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes payment events to the outbox_events table (Transactional Outbox, CODING_CONVENTION §24).
 * Must be called inside the business transaction that changes the payment, so the event is stored
 * if and only if the payment is; {@link OutboxEventPoller} publishes it to Kafka afterwards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    public static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    public static final String PRODUCER_NAME = "payment-service";
    public static final String AGGREGATE_TYPE = "Payment";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publishPaymentSucceeded(Payment payment, String causationId) {
        PaymentSucceededPayload payload = PaymentSucceededPayload.builder()
                .paymentId(payment.getPaymentId())
                .tripId(payment.getTripId())
                .passengerId(payment.getPassengerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .processedAt(payment.getCapturedAt())
                .build();
        saveToOutbox(PAYMENT_SUCCEEDED, payment, causationId, payload);
    }

    public void publishPaymentFailed(Payment payment, int retryCount, String causationId) {
        PaymentFailedPayload payload = PaymentFailedPayload.builder()
                .paymentId(payment.getPaymentId())
                .tripId(payment.getTripId())
                .passengerId(payment.getPassengerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .reason(payment.getFailureReason())
                .retryCount(retryCount)
                .failedAt(Instant.now())
                .build();
        saveToOutbox(PAYMENT_FAILED, payment, causationId, payload);
    }

    private <T> void saveToOutbox(String eventType, Payment payment, String causationId, T payload) {
        UUID eventId = UUID.randomUUID();
        EventEnvelope<T> envelope = EventEnvelope.<T>builder()
                .eventId(eventId.toString())
                .eventType(eventType)
                .aggregateId(payment.getPaymentId().toString())
                .aggregateType(AGGREGATE_TYPE)
                .producer(PRODUCER_NAME)
                .correlationId(payment.getCorrelationId())
                .causationId(causationId)
                .schemaVersion("1.0")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(payment.getPaymentId())
                .eventType(eventType)
                .payload(objectMapper.writeValueAsString(envelope))
                .correlationId(payment.getCorrelationId())
                .build();
        outboxEventRepository.save(outboxEvent);

        log.info("Event stored in outbox: eventType={}, eventId={}, paymentId={}, tripId={}, correlationId={}",
                eventType, eventId, payment.getPaymentId(), payment.getTripId(), payment.getCorrelationId());
    }
}
