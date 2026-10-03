package com.example.matchingservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "offer_id", updatable = false, nullable = false)
    private UUID offerId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OfferStatus status = OfferStatus.PENDING;

    @Column(name = "driver_version_used", nullable = false)
    private Integer driverVersionUsed;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "rank", nullable = false)
    @Builder.Default
    private Integer rank = 1;

    @Column(name = "offered_at", nullable = false, updatable = false)
    private Instant offeredAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    public void prePersist() {
        if (this.offeredAt == null) {
            this.offeredAt = Instant.now();
        }
        if (this.status == null) {
            this.status = OfferStatus.PENDING;
        }
        if (this.rank == null) {
            this.rank = 1;
        }
        if (this.driverVersionUsed == null) {
            this.driverVersionUsed = 1;
        }
    }
}
