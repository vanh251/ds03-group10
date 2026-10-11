package com.example.tripservice.repository;

import com.example.tripservice.entity.SagaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SagaLogRepository extends JpaRepository<SagaLog, UUID> {
}
