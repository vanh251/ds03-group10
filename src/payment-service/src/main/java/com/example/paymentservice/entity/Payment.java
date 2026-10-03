package com.example.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_trip_id", columnList = "trip_id"),
        @Index(name = "idx_payments_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_payments_status", columnList = "status"),
        @Index(name = "idx_payments_correlation", columnList = "correlation_id"),
        @Index(name = "uq_payments_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(name = "payment_id", length = 64, nullable = false)
    private String paymentId;

    @Column(name = "trip_id", length = 64, nullable = false)
    private String tripId;

    @Column(name = "passenger_id", length = 64, nullable = false)
    private String passengerId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentStatus status;

    @Column(name = "payment_method", length = 50)
    @Builder.Default
    private String paymentMethod = "MOCK_CARD";

    @Column(name = "gateway_ref", length = 255)
    private String gatewayRef;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "correlation_id", length = 64, nullable = false)
    private String correlationId;

    @Column(name = "idempotency_key", length = 255, nullable = false, unique = true)
    private String idempotencyKey;

    @Version
    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
