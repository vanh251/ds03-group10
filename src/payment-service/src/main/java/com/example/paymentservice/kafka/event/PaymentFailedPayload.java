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
public class PaymentFailedPayload {

    private String paymentId;
    private String tripId;
    private String passengerId;
    private Double amount;
    private String currency;
    private String reason;
    private Integer retryCount;
    private Instant failedAt;
}
