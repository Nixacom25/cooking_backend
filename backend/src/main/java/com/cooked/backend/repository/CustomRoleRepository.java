package com.cooked.backend.repository;

import com.cooked.backend.entity.CustomRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomRoleRepository extends JpaRepository<CustomRole, UUID> {
    List<CustomRole> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
