package com.example.paymentservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Payload of PaymentSucceeded (docs/events.md §4.6)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededPayload {
    private UUID paymentId;
    private UUID tripId;
    private UUID passengerId;
    private BigDecimal amount;
    private String currency;
    private Instant processedAt;
}
