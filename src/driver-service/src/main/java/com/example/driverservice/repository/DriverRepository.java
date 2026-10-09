package com.example.driverservice.repository;

import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findByStatus(DriverStatus status);

    // BETWEEN never matches NULL, so drivers that never reported a position are left out
    List<Driver> findByStatusAndCurrentLatBetweenAndCurrentLngBetween(
            DriverStatus status, BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);
}
