package com.example.driverservice.repository;

import com.example.driverservice.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    boolean existsByUserId(UUID userId);

    Optional<Driver> findByUserId(UUID userId);
}
