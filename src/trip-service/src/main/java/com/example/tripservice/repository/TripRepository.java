package com.example.tripservice.repository;

import com.example.tripservice.entity.Trip;
import com.example.tripservice.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findByPassengerId(UUID passengerId);
    List<Trip> findByDriverId(UUID driverId);
    List<Trip> findByStatus(TripStatus status);
}
