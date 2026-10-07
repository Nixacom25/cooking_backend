package com.cooked.backend.repository;

import com.cooked.backend.entity.DripSend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DripSendRepository extends JpaRepository<DripSend, UUID> {
    boolean existsByUserIdAndStep(UUID userId, String step);
}
