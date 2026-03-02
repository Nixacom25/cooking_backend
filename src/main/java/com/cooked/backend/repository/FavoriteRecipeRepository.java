package com.cooked.backend.repository;

import com.cooked.backend.entity.FavoriteRecipe;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FavoriteRecipeRepository extends JpaRepository<FavoriteRecipe, UUID> {
    Optional<FavoriteRecipe> findByUserAndRecipe(User user, Recipe recipe);

    Page<FavoriteRecipe> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    boolean existsByUserAndRecipe(User user, Recipe recipe);
}
