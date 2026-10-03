package com.example.matchingservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatchingEventPublisher {

    private static final String MATCHING_EVENTS_TOPIC = "matching-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MatchingEventPublisher(@Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public <T> void publish(EventEnvelope<T> event) {
        log.info("Publishing matching event: type={}, aggregateId={}, correlationId={}",
                event.getEventType(), event.getAggregateId(), event.getCorrelationId());

        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(MATCHING_EVENTS_TOPIC, event.getAggregateId(), event);
                log.debug("Event {} published to topic {}", event.getEventType(), MATCHING_EVENTS_TOPIC);
            } catch (Exception e) {
                log.warn("Failed to publish matching event to Kafka (topic: {}): {}", MATCHING_EVENTS_TOPIC, e.getMessage());
            }
        } else {
            log.debug("KafkaTemplate not configured; skipping Kafka publish for event {}", event.getEventType());
        }
    }
}
