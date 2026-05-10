package com.cooked.backend.repository;

import com.cooked.backend.entity.Cookbook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface CookbookRepository extends JpaRepository<Cookbook, UUID> {
    Optional<Cookbook> findByUserIdAndName(UUID userId, String name);
    long countByUserId(UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    List<Cookbook> findAllByUserId(UUID userId);
}
