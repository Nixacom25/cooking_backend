package com.cooked.backend.repository;

import com.cooked.backend.entity.CriticalError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CriticalErrorRepository extends JpaRepository<CriticalError, UUID> {
    Page<CriticalError> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<CriticalError> findAllByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}