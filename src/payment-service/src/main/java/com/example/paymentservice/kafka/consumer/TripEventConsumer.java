package com.example.paymentservice.kafka.consumer;

import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventConsumer {

    public static final String TRIP_EVENTS_TOPIC = "trip-events";
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TRIP_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id:payment-service-group}")
    public void consumeTripEvent(String message) {
        try {
            JsonNode rootNode = objectMapper.readTree(message);
            String eventType = rootNode.path("eventType").asText();
            String eventId = rootNode.path("eventId").asText();
            String correlationId = rootNode.path("correlationId").asText();

            if (correlationId != null && !correlationId.isEmpty()) {
                MDC.put("correlationId", correlationId);
            }

            log.info("Received message from {}: eventType={}, eventId={}, correlationId={}",
                    TRIP_EVENTS_TOPIC, eventType, eventId, correlationId);

            if ("TripCompleted".equalsIgnoreCase(eventType) || "trip.completed".equalsIgnoreCase(eventType)) {
                JsonNode payloadNode = rootNode.path("payload");
                String tripId = payloadNode.path("tripId").asText();
                String passengerId = payloadNode.path("passengerId").asText();
                Double finalFare = payloadNode.path("finalFare").asDouble();
                String currency = payloadNode.path("currency").asText("VND");

                log.info("Processing TripCompleted event for tripId={}, finalFare={}", tripId, finalFare);
                paymentService.processTripCompleted(eventId, tripId, passengerId, finalFare, currency, correlationId);
            }
        } catch (Exception e) {
            log.error("Error processing trip event message: {}", message, e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
