package com.cooked.backend.repository;

import com.cooked.backend.entity.AmbassadorPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmbassadorPayoutRepository extends JpaRepository<AmbassadorPayout, UUID> {
    List<AmbassadorPayout> findByAmbassadorId(UUID ambassadorId);
    Optional<AmbassadorPayout> findByAmbassadorIdAndMonth(UUID ambassadorId, String month);
}
