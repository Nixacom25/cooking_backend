package com.cooked.backend.repository;

import com.cooked.backend.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {
    Page<ActivityLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM ActivityLog a WHERE a.user.role = :role ORDER BY a.createdAt DESC")
    Page<ActivityLog> findAllByUserRoleOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role, Pageable pageable);
}
