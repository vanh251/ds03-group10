package com.example.matchingservice.event;

import com.example.matchingservice.entity.OutboxEvent;
import com.example.matchingservice.repository.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class MatchingEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public MatchingEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public <T> void publish(EventEnvelope<T> event) {
        log.info("Saving matching event to outbox: type={}, aggregateId={}, correlationId={}",
                event.getEventType(), event.getAggregateId(), event.getCorrelationId());
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(event.getEventId())
                    .aggregateId(event.getAggregateId())
                    .aggregateType(event.getAggregateType())
                    .eventType(event.getEventType())
                    .payload(eventJson)
                    .createdAt(Instant.now())
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to save event to outbox", e);
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
