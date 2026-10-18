package com.example.paymentservice.kafka.producer;

import com.example.paymentservice.entity.OutboxEvent;
import com.example.paymentservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes unpublished outbox_events rows to Kafka in creation order and marks them published.
 * A row is marked only after the broker acknowledged the send, so a crash in between re-sends
 * the same eventId later; consumers drop the duplicate via processed_events (docs/events.md §7).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPoller {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.outbox.send-timeout-ms:5000}")
    private long sendTimeoutMs;

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            if (!publish(event)) {
                // Stop at the first failure so later events of the same trip are not sent before it
                return;
            }
        }
    }

    private boolean publish(OutboxEvent event) {
        String partitionKey = resolvePartitionKey(event);
        try {
            kafkaTemplate.send(PaymentEventProducer.PAYMENT_EVENTS_TOPIC, partitionKey, event.getPayload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            event.setPublished(true);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);
            log.info("Published outbox event: topic={}, key={}, eventType={}, eventId={}, correlationId={}",
                    PaymentEventProducer.PAYMENT_EVENTS_TOPIC, partitionKey, event.getEventType(),
                    event.getEventId(), event.getCorrelationId());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure(event, e);
            return false;
        } catch (Exception e) {
            recordFailure(event, e);
            return false;
        }
    }

    private void recordFailure(OutboxEvent event, Exception e) {
        event.setRetryCount(event.getRetryCount() + 1);
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        event.setLastError(error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error);
        outboxEventRepository.save(event);
        log.error("Failed to publish outbox event: eventType={}, eventId={}, attempt={}, correlationId={}",
                event.getEventType(), event.getEventId(), event.getRetryCount(), event.getCorrelationId(), e);
    }

    // Payment events are keyed by tripId so all events of one trip stay ordered on one partition
    private String resolvePartitionKey(OutboxEvent event) {
        String tripId = objectMapper.readTree(event.getPayload()).path("payload").path("tripId").asText("");
        return tripId.isBlank() ? event.getAggregateId().toString() : tripId;
    }
}
