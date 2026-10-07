package com.cooked.backend.repository;

import com.cooked.backend.entity.Ambassador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmbassadorRepository extends JpaRepository<Ambassador, UUID> {
    Optional<Ambassador> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Ambassador> findAllByOrderByCreatedAtDesc();
}
