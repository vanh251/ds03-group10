package com.example.driverservice.repository;

import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    // BETWEEN never matches NULL, so drivers that never reported a position are left out
    List<Driver> findByStatusAndCurrentLatBetweenAndCurrentLngBetween(
            DriverStatus status, BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);

    /**
     * Stores a position report. A bulk JPQL update does not touch @Version on purpose: version guards status changes
     * (the reservation CAS), and frequent heartbeats must not make a reservation fail. Returns the rows updated.
     */
    @Modifying
    @Query("UPDATE Driver d SET d.currentLat = :lat, d.currentLng = :lng, d.lastHeartbeatAt = :now, d.updatedAt = :now "
            + "WHERE d.driverId = :driverId")
    int updateLocation(@Param("driverId") UUID driverId, @Param("lat") BigDecimal lat,
                       @Param("lng") BigDecimal lng, @Param("now") Instant now);
}
