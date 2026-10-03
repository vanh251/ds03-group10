package com.example.matchingservice.repository;

import com.example.matchingservice.entity.DriverOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DriverOfferRepository extends JpaRepository<DriverOffer, UUID> {
    List<DriverOffer> findByRequestIdOrderByRankAsc(UUID requestId);
    List<DriverOffer> findByTripId(UUID tripId);
}
