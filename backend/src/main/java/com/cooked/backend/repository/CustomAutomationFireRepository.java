package com.cooked.backend.repository;

import com.cooked.backend.entity.CustomAutomationFire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface CustomAutomationFireRepository extends JpaRepository<CustomAutomationFire, UUID> {
    long countByAutomationIdAndFiredAtAfter(UUID automationId, LocalDateTime after);
    boolean existsByAutomationIdAndUserIdIsNullAndFiredAtAfter(UUID automationId, LocalDateTime after);

    long countByAutomationIdAndUserIdIsNotNullAndFiredAtAfter(UUID automationId, LocalDateTime after);
}
