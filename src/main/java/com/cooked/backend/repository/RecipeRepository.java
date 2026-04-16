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

        org.springframework.data.domain.Page<Recipe> findByIsPublicTrueOrderByCreatedAtDesc(
                        org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT u, COUNT(DISTINCT r), " +
                        "((SELECT COUNT(f) FROM FavoriteRecipe f WHERE f.recipe.user.id = u.id AND f.recipe.isPublic = true) + "
                        +
                        "(SELECT COUNT(gi) FROM GroceryItem gi WHERE gi.recipe.user.id = u.id AND gi.recipe.isPublic = true)) as usageCount "
                        +
                        "FROM Recipe r JOIN r.user u " +
                        "WHERE r.isPublic = true " +
                        "GROUP BY u.id, u.firstname, u.lastname, u.photo " +
                        "ORDER BY usageCount DESC")
        org.springframework.data.domain.Page<Object[]> findTopCreators(
                        org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT r, " +
                        "(SELECT COUNT(f) FROM FavoriteRecipe f WHERE f.recipe = r) + " +
                        "(SELECT COUNT(gi) FROM GroceryItem gi WHERE gi.recipe = r) as usageCount " +
                        "FROM Recipe r " +
                        "WHERE r.isPublic = true " +
                        "AND (:category IS NULL OR r.category = :category) " +
                        "ORDER BY usageCount DESC")
        org.springframework.data.domain.Page<Object[]> findPopularRecipes(
                        @org.springframework.data.repository.query.Param("category") String category,
                        org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<Recipe> findByUserIdAndSourceUrlIsNotNullOrderByCreatedAtDesc(
                        UUID userId, org.springframework.data.domain.Pageable pageable);
}
