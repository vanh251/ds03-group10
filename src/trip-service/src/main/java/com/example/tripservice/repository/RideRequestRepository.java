package com.example.tripservice.repository;

import com.example.tripservice.entity.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, UUID> {
    Optional<RideRequest> findByTripId(UUID tripId);
}
