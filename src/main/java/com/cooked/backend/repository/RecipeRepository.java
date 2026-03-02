package com.cooked.backend.repository;

import com.cooked.backend.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
    Optional<Recipe> findByUserIdAndName(UUID userId, String name);

    boolean existsByUserIdAndName(UUID userId, String name);

    List<Recipe> findAllByUserId(UUID userId);

    org.springframework.data.domain.Page<Recipe> findByIsPublicTrue(org.springframework.data.domain.Pageable pageable);
}
