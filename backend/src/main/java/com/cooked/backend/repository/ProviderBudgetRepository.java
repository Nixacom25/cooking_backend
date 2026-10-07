package com.cooked.backend.repository;

import com.cooked.backend.entity.ProviderBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderBudgetRepository extends JpaRepository<ProviderBudget, UUID> {
    Optional<ProviderBudget> findByProviderIgnoreCase(String provider);
}
