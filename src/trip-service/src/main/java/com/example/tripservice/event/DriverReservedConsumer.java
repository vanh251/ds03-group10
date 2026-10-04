package com.example.tripservice.event;

import com.example.tripservice.entity.ProcessedEvent;
import com.example.tripservice.repository.ProcessedEventRepository;
import com.example.tripservice.service.TripService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.kafka.annotation.KafkaListener")
public class DriverReservedConsumer {

    private final TripService tripService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "matching-events", groupId = "${spring.kafka.consumer.group-id:trip-service-group}")
    @Transactional
    public void handleDriverReserved(ConsumerRecord<String, String> record) {
        log.info("Received matching event from Kafka: topic={}, partition={}, key={}",
                record.topic(), record.partition(), record.key());

        String message = record.value();
        
        try {
            EventEnvelope<Map<String, Object>> envelope = objectMapper.readValue(message, new TypeReference<EventEnvelope<Map<String, Object>>>() {});
            
            if (!"DriverReserved".equals(envelope.getEventType())) {
                log.debug("Ignoring non-DriverReserved event: {}", envelope.getEventType());
                return;
            }

            String eventId = envelope.getEventId().toString();
            if (processedEventRepository.existsById(eventId)) {
                log.info("Duplicate event detected, skipping: {}", eventId);
                return;
            }
            
            if (envelope.getCorrelationId() != null) {
                MDC.put("correlationId", envelope.getCorrelationId());
            }

            Map<String, Object> payload = envelope.getPayload();
            if (payload != null && payload.containsKey("tripId") && payload.containsKey("driverId")) {
                UUID tripId = UUID.fromString(payload.get("tripId").toString());
                UUID driverId = UUID.fromString(payload.get("driverId").toString());

                log.info("Processing DriverReserved event: assigning driverId={} to tripId={}", driverId, tripId);
                tripService.assignDriver(tripId, driverId);
                
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(envelope.getEventType())
                        .processedAt(Instant.now())
                        .build());
            } else {
                log.warn("Could not extract tripId or driverId from payload: {}", payload);
            }
        } catch (Exception e) {
            log.error("Error processing DriverReserved event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process event", e); // Throw to trigger Kafka retry
        } finally {
            MDC.remove("correlationId");
        }
    }
}
