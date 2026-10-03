package com.example.paymentservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {

    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private String producer;
    private String correlationId;
    private String causationId;
    @Builder.Default
    private String schemaVersion = "1.0";
    private Instant occurredAt;
    private T payload;
}
