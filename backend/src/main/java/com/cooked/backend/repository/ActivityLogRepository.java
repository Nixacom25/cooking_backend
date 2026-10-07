package com.cooked.backend.repository;

import com.cooked.backend.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ActivityLog> {

    /** People with activity for a role: [email, firstname, lastname]. */
    @org.springframework.data.jpa.repository.Query("select distinct u.email, u.firstname, u.lastname from ActivityLog a join a.user u where u.role = :role order by u.email")
    java.util.List<Object[]> findPeopleByRole(@org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role);

    @org.springframework.data.jpa.repository.Query("select distinct upper(a.entityType) from ActivityLog a where a.user.role = :role and a.entityType is not null order by upper(a.entityType)")
    java.util.List<String> findAreasByRole(@org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role);

    @org.springframework.data.jpa.repository.Query("select distinct a.title from ActivityLog a where a.user.role = :role and a.title is not null order by a.title")
    java.util.List<String> findActionsByRole(@org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role);
    Page<ActivityLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM ActivityLog a WHERE a.user.role = :role AND a.message NOT LIKE 'User logged in%' ORDER BY a.createdAt DESC")
    Page<ActivityLog> findAllByUserRoleOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role, Pageable pageable);
}
