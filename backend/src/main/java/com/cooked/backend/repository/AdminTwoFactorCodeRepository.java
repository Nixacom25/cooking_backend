package com.cooked.backend.repository;

import com.cooked.backend.entity.AdminTwoFactorCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AdminTwoFactorCodeRepository extends JpaRepository<AdminTwoFactorCode, UUID> {

    Optional<AdminTwoFactorCode> findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime after);
}
