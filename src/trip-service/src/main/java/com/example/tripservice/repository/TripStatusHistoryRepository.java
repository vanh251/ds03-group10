package com.example.tripservice.repository;

import com.example.tripservice.entity.TripStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripStatusHistoryRepository extends JpaRepository<TripStatusHistory, UUID> {
    List<TripStatusHistory> findByTripIdOrderByChangedAtAsc(UUID tripId);
}
