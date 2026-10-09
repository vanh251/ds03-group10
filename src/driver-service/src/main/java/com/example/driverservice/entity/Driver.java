package com.example.driverservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Driver profile (docs/database_schema.md, table drivers). user_id is a soft reference to identity_db.users.
 */
@Entity
@Table(name = "drivers",
        uniqueConstraints = @UniqueConstraint(name = Driver.UQ_USER_ID, columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    public static final String UQ_USER_ID = "uq_drivers_user_id";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "driver_id", updatable = false, nullable = false)
    private UUID driverId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DriverStatus status;

    @Column(name = "reserved_for_trip")
    private UUID reservedForTrip;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "current_lat", precision = 9, scale = 6)
    private BigDecimal currentLat;

    @Column(name = "current_lng", precision = 9, scale = 6)
    private BigDecimal currentLng;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.status == null) {
            this.status = DriverStatus.OFFLINE;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
