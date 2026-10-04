package com.example.matchingservice.event;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEnvelope<T> {
    private UUID eventId;
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
