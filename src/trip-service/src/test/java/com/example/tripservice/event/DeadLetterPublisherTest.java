package com.example.tripservice.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeadLetterPublisherTest {

    private final ObjectMapper objectMapper = new JsonMapper();

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("accept after retries sends the docs/events.md §6 message to <topic>.DLQ with the consumer group header")
    void accept_afterRetries_publishesDeadLetterMessageWithGroupHeader() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(template);
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        DeadLetterPublisher publisher = new DeadLetterPublisher(provider, objectMapper, "trip-service-group");

        ConsumerRecord<String, String> record = new ConsumerRecord<>("matching-events", 1, 42L, "trip-1",
                "{\"eventId\":\"e1\",\"eventType\":\"DriverReserved\",\"correlationId\":\"corr-1\"}");
        Exception failure = new RuntimeException("listener failed", new IllegalStateException("trip not found"));
        for (int attempt = 1; attempt <= 4; attempt++) {
            publisher.failedDelivery(record, failure, attempt);
        }
        publisher.accept(record, failure);

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(sent.capture());
        assertEquals("matching-events.DLQ", sent.getValue().topic());
        assertEquals("trip-1", sent.getValue().key());
        assertEquals("trip-service-group", new String(
                sent.getValue().headers().lastHeader(DeadLetterPublisher.CONSUMER_GROUP_HEADER).value(), StandardCharsets.UTF_8));

        JsonNode message = objectMapper.readTree(sent.getValue().value());
        assertEquals("matching-events", message.path("originalTopic").asText());
        assertEquals(42L, message.path("originalOffset").asLong());
        assertEquals("e1", message.path("originalMessage").path("eventId").asText());
        assertEquals("IllegalStateException: trip not found", message.path("error").asText());
        assertEquals(3, message.path("retryCount").asInt());
        assertEquals("corr-1", message.path("correlationId").asText());
    }
}
