package com.example.paymentservice;

import com.example.paymentservice.exception.ValidationException;
import com.example.paymentservice.kafka.producer.DeadLetterPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new JsonMapper();

    private DeadLetterPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DeadLetterPublisher(kafkaTemplate, objectMapper);
    }

    @Test
    @DisplayName("accept after retries sends the docs/events.md §6 DLQ message to <topic>.DLQ")
    void accept_afterRetries_publishesDeadLetterMessage() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trip-events", 2, 123L, "trip-001",
                "{\"eventId\":\"evt-005\",\"eventType\":\"TripCompleted\",\"correlationId\":\"corr-001\"}");
        when(kafkaTemplate.send(eq("trip-events.DLQ"), eq("trip-001"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Exception failure = new ListenerExecutionFailedException("listener failed", new IllegalStateException("database down"));
        for (int attempt = 1; attempt <= 4; attempt++) {
            publisher.failedDelivery(record, failure, attempt);
        }
        publisher.accept(record, failure);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("trip-events.DLQ"), eq("trip-001"), sent.capture());
        JsonNode message = objectMapper.readTree(sent.getValue());
        assertEquals("trip-events", message.path("originalTopic").asText());
        assertEquals(2, message.path("originalPartition").asInt());
        assertEquals(123L, message.path("originalOffset").asLong());
        assertEquals("evt-005", message.path("originalMessage").path("eventId").asText());
        assertEquals("IllegalStateException: database down", message.path("error").asText());
        assertEquals(3, message.path("retryCount").asInt());
        assertEquals("corr-001", message.path("correlationId").asText());
        assertFalse(message.path("firstFailedAt").isMissingNode());
        assertFalse(message.path("lastFailedAt").isMissingNode());
    }

    @Test
    @DisplayName("accept of a non-retryable record keeps raw text and reports retryCount 0")
    void accept_notRetryableInvalidJson_keepsRawMessage() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trip-events", 0, 7L, null, "not-json");
        when(kafkaTemplate.send(eq("trip-events.DLQ"), eq(null), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.accept(record, new ValidationException("Message on trip-events is not valid JSON"));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("trip-events.DLQ"), eq(null), sent.capture());
        JsonNode message = objectMapper.readTree(sent.getValue());
        assertEquals("not-json", message.path("originalMessage").asText());
        assertEquals(0, message.path("retryCount").asInt());
    }

    @Test
    @DisplayName("accept rethrows when the DLQ send fails so the offset is not committed")
    void accept_dlqSendFails_throws() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trip-events", 0, 8L, "trip-001", "{}");
        when(kafkaTemplate.send(eq("trip-events.DLQ"), eq("trip-001"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThrows(IllegalStateException.class, () -> publisher.accept(record, new RuntimeException("boom")));
    }
}
