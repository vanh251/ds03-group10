package com.example.driverservice.repository;

import com.example.driverservice.entity.Driver;
import com.example.driverservice.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    boolean existsByLicensePlate(String licensePlate);

    List<Vehicle> findByDriver(Driver driver);
}
