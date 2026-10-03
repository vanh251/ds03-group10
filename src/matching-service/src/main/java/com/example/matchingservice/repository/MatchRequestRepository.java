package com.example.matchingservice.repository;

import com.example.matchingservice.entity.MatchRequest;
import com.example.matchingservice.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRequestRepository extends JpaRepository<MatchRequest, UUID> {
    Optional<MatchRequest> findByTripId(UUID tripId);
}
