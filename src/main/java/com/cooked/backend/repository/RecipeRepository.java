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

        @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r WHERE r.isPublic = true AND r.origin = :origin " +
                        "AND (:cuisine IS NULL OR r.cuisine = :cuisine) " +
                        "ORDER BY r.createdAt DESC")
        org.springframework.data.domain.Page<Recipe> findExploreRecipes(
                        @org.springframework.data.repository.query.Param("origin") com.cooked.backend.entity.RecipeOrigin origin,
                        @org.springframework.data.repository.query.Param("cuisine") String cuisine,
                        org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<Recipe> findByOriginOrderByCreatedAtDesc(
                        com.cooked.backend.entity.RecipeOrigin origin,
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
                        "AND (COALESCE(:cuisines, NULL) IS NULL OR r.cuisine IN :cuisines) " +
                        "ORDER BY usageCount DESC")
        org.springframework.data.domain.Page<Object[]> findPopularRecipes(
                        @org.springframework.data.repository.query.Param("category") String category,
                        @org.springframework.data.repository.query.Param("cuisines") List<String> cuisines,
                        org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<Recipe> findByUserIdAndSourceUrlIsNotNullOrderByCreatedAtDesc(
                        UUID userId, org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<Recipe> findByUserIdAndOriginOrderByCreatedAtDesc(
                        UUID userId, com.cooked.backend.entity.RecipeOrigin origin, org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.transaction.annotation.Transactional
        int deleteByOriginAndExpiresAtBefore(com.cooked.backend.entity.RecipeOrigin origin, java.time.LocalDateTime now);
        
        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.transaction.annotation.Transactional
        int deleteByOrigin(com.cooked.backend.entity.RecipeOrigin origin);
        
        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.transaction.annotation.Transactional
        void deleteByUserId(UUID userId);

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.cuisine FROM Recipe r WHERE r.origin = 'EXPLORE' AND r.cuisine IS NOT NULL")
        List<String> findDistinctCuisines();

    @org.springframework.data.jpa.repository.Query("SELECT r.category, COUNT(r) FROM Recipe r WHERE r.origin = 'EXPLORE' AND r.category IS NOT NULL GROUP BY r.category")
    List<Object[]> findCategoriesWithCount();

    @org.springframework.data.jpa.repository.Query("SELECT r.cuisine, COUNT(r) FROM Recipe r WHERE r.origin = 'EXPLORE' AND r.cuisine IS NOT NULL GROUP BY r.cuisine")
    List<Object[]> findCuisinesWithCount();
}
