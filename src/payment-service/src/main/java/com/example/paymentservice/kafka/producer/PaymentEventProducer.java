package com.example.paymentservice.kafka.producer;

import com.example.paymentservice.kafka.event.EventEnvelope;
import com.example.paymentservice.kafka.event.PaymentFailedPayload;
import com.example.paymentservice.kafka.event.PaymentSucceededPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    public static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    public static final String PRODUCER_NAME = "payment-service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishPaymentSucceeded(String paymentId, String tripId, String passengerId,
                                        Double amount, String currency, String correlationId, String causationId) {
        String eventId = UUID.randomUUID().toString();
        PaymentSucceededPayload payload = PaymentSucceededPayload.builder()
                .paymentId(paymentId)
                .tripId(tripId)
                .passengerId(passengerId)
                .amount(amount)
                .currency(currency != null ? currency : "VND")
                .processedAt(Instant.now())
                .build();

        EventEnvelope<PaymentSucceededPayload> envelope = EventEnvelope.<PaymentSucceededPayload>builder()
                .eventId(eventId)
                .eventType("PaymentSucceeded")
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .producer(PRODUCER_NAME)
                .correlationId(correlationId)
                .causationId(causationId)
                .schemaVersion("1.0")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        sendEvent(tripId, envelope);
    }

    public void publishPaymentFailed(String paymentId, String tripId, String passengerId,
                                     Double amount, String currency, String reason,
                                     String correlationId, String causationId) {
        String eventId = UUID.randomUUID().toString();
        PaymentFailedPayload payload = PaymentFailedPayload.builder()
                .paymentId(paymentId)
                .tripId(tripId)
                .passengerId(passengerId)
                .amount(amount)
                .currency(currency != null ? currency : "VND")
                .reason(reason != null ? reason : "PAYMENT_FAILED")
                .retryCount(0)
                .failedAt(Instant.now())
                .build();

        EventEnvelope<PaymentFailedPayload> envelope = EventEnvelope.<PaymentFailedPayload>builder()
                .eventId(eventId)
                .eventType("PaymentFailed")
                .aggregateId(paymentId)
                .aggregateType("Payment")
                .producer(PRODUCER_NAME)
                .correlationId(correlationId)
                .causationId(causationId)
                .schemaVersion("1.0")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        sendEvent(tripId, envelope);
    }

    private <T> void sendEvent(String partitionKey, EventEnvelope<T> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            log.info("Publishing event to {}: key={}, eventType={}, eventId={}, correlationId={}",
                    PAYMENT_EVENTS_TOPIC, partitionKey, envelope.getEventType(), envelope.getEventId(), envelope.getCorrelationId());
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, partitionKey, json);
        } catch (Exception e) {
            log.error("Failed to serialize or send Kafka event: eventType={}, key={}",
                    envelope.getEventType(), partitionKey, e);
        }
    }
}
