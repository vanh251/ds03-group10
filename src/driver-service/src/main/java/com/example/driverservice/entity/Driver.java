package com.example.driverservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Driver profile (docs/database_schema.md, table drivers).
 * driver_id is the driver's identity user id (soft reference to identity_db.users.user_id), assigned at
 * registration rather than generated: one profile per user, and the same id appears in the JWT (sub),
 * X-User-Id, URLs (/api/drivers/{id}) and events (driverId).
 */
@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    /** PostgreSQL's default name for the primary key constraint of this table. */
    public static final String PRIMARY_KEY = "drivers_pkey";

    @Id
    @Column(name = "driver_id", updatable = false, nullable = false)
    private UUID driverId;

    // Copy of identity_db.users.full_name taken at registration, so driver lists need no call to identity-service
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

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
