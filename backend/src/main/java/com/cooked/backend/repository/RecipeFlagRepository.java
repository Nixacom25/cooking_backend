package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeFlagRepository extends JpaRepository<RecipeFlag, UUID> {

    List<RecipeFlag> findByRecipeIdOrderByCreatedAtDesc(UUID recipeId);

    long countByResolvedAtIsNull();

    List<RecipeFlag> findTop100ByResolvedAtIsNullOrderByCreatedAtDesc();
}
