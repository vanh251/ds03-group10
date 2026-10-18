package com.example.paymentservice.repository;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findTopByTripIdOrderByCreatedAtDesc(UUID tripId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByTripIdAndStatus(UUID tripId, PaymentStatus status);
}
