package com.example.tripservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class TripEventPublisher {

    private static final String TRIP_EVENTS_TOPIC = "trip-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TripEventPublisher(@Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public <T> void publish(EventEnvelope<T> event) {
        log.info("Publishing event: type={}, aggregateId={}, correlationId={}",
                event.getEventType(), event.getAggregateId(), event.getCorrelationId());

        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(TRIP_EVENTS_TOPIC, event.getAggregateId(), event);
                log.debug("Event {} published to topic {}", event.getEventType(), TRIP_EVENTS_TOPIC);
            } catch (Exception e) {
                log.warn("Failed to publish event to Kafka (topic: {}): {}", TRIP_EVENTS_TOPIC, e.getMessage());
            }
        } else {
            log.debug("KafkaTemplate not configured; skipping Kafka publish for event {}", event.getEventType());
        }
    }
}
