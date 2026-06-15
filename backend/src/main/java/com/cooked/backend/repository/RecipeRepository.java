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
        long countByUserId(UUID userId);
        long countByUserIdAndOrigin(UUID userId, com.cooked.backend.entity.RecipeOrigin origin);

        long countByOrigin(com.cooked.backend.entity.RecipeOrigin origin);

        boolean existsByUserIdAndName(UUID userId, String name);
        boolean existsByNameAndOrigin(String name, com.cooked.backend.entity.RecipeOrigin origin);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
        List<Recipe> findAllByUserId(UUID userId);
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
        List<Recipe> findAllByUserIdAndOriginNot(UUID userId, com.cooked.backend.entity.RecipeOrigin origin);
        
        Optional<Recipe> findFirstByUserIdAndOriginNotInOrderByCreatedAtAsc(UUID userId, List<com.cooked.backend.entity.RecipeOrigin> origins);
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
        List<Recipe> findAllByUserIdAndOriginOrderByCreatedAtDesc(UUID userId, com.cooked.backend.entity.RecipeOrigin origin);

        @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r WHERE r.isPublic = true " +
                        "AND r.status = true " +
                        "AND (r.origin = :origin1 OR r.origin = :origin2) " +
                        "AND (:cuisine IS NULL OR r.cuisine.name = :cuisine) " +
                        "AND (:category IS NULL OR :category IN (SELECT c.name FROM r.categories c)) " +
                        "ORDER BY r.createdAt DESC")
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
        org.springframework.data.domain.Page<Recipe> findExploreRecipes(
                        @org.springframework.data.repository.query.Param("origin1") com.cooked.backend.entity.RecipeOrigin origin1,
                        @org.springframework.data.repository.query.Param("origin2") com.cooked.backend.entity.RecipeOrigin origin2,
                        @org.springframework.data.repository.query.Param("cuisine") String cuisine,
                        @org.springframework.data.repository.query.Param("category") String category,
                        org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
        org.springframework.data.domain.Page<Recipe> findByOriginOrderByCreatedAtDesc(
                        com.cooked.backend.entity.RecipeOrigin origin,
                        org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT u, COUNT(DISTINCT r), " +
                        "(SELECT COUNT(gi) FROM GroceryItem gi WHERE gi.recipe.user.id = u.id AND gi.recipe.isPublic = true) as usageCount "
                        +
                        "FROM Recipe r JOIN r.user u " +
                        "WHERE r.isPublic = true " +
                        "GROUP BY u.id, u.firstname, u.lastname, u.photo " +
                        "ORDER BY usageCount DESC")
        org.springframework.data.domain.Page<Object[]> findTopCreators(
                        org.springframework.data.domain.Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r " +
                        "WHERE r.isPublic = true " +
                        "AND (:category IS NULL OR :category IN (SELECT c.name FROM r.categories c)) " +
                        "AND (:cuisines IS NULL OR (SELECT COUNT(r2) FROM Recipe r2 WHERE r2.id = r.id AND r2.cuisine.name IN :cuisines) > 0) " +
                        "ORDER BY RANDOM()")
        org.springframework.data.domain.Page<Recipe> findRandomPopularRecipes(
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
        @org.springframework.data.jpa.repository.Query("UPDATE Recipe r SET r.origin = 'SCAN' WHERE r.origin = 'MANUAL' OR r.origin IS NULL")
        int migrateOriginsToScan();

        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.data.jpa.repository.Query("DELETE FROM Recipe r WHERE r.origin = :origin AND r.expiresAt < :now " +
                        "AND NOT EXISTS (SELECT 1 FROM GroceryItem gi WHERE gi.recipe = r)")
        int deleteExpiredSuggestedRecipes(
                        @org.springframework.data.repository.query.Param("origin") com.cooked.backend.entity.RecipeOrigin origin,
                        @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
        
        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.transaction.annotation.Transactional
        int deleteByOrigin(com.cooked.backend.entity.RecipeOrigin origin);
        
        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.transaction.annotation.Transactional
        void deleteByUserId(UUID userId);

        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.cuisine.name FROM Recipe r WHERE r.origin = 'EXPLORE' AND r.cuisine IS NOT NULL")
        List<String> findDistinctCuisines();

    @org.springframework.data.jpa.repository.Query("SELECT c.name, c.image, COUNT(r) FROM Recipe r JOIN r.categories c WHERE r.origin = 'EXPLORE' AND c.type = com.cooked.backend.entity.CategoryType.CATEGORY AND c.active = true GROUP BY c.name, c.image ORDER BY COUNT(r) DESC")
    List<Object[]> findCategoriesWithCount();

    @org.springframework.data.jpa.repository.Query("SELECT c.name, c.image, COUNT(r) FROM Recipe r JOIN r.cuisine c WHERE r.origin = 'EXPLORE' AND c.type = com.cooked.backend.entity.CategoryType.CUISINE AND c.active = true GROUP BY c.name, c.image ORDER BY COUNT(r) DESC")
    List<Object[]> findCuisinesWithCount();

    List<Recipe> findByNameContainingIgnoreCase(String name);

    @org.springframework.data.jpa.repository.Query(
        "SELECT r FROM Recipe r WHERE r.origin = 'EXPLORE' " +
        "AND r.status = true " +
        "AND r.image IS NOT NULL AND r.image != '' " +
        "AND r.image NOT LIKE '%unsplash%' AND r.image NOT LIKE '%splash%' " +
        "AND (" +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :kw1, '%')) OR " +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :kw2, '%')) OR " +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :kw3, '%')) OR " +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :kw4, '%')) OR " +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :kw5, '%'))" +
        ")")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
    List<Recipe> findExploreRecipesByKeywords(
        @org.springframework.data.repository.query.Param("kw1") String kw1,
        @org.springframework.data.repository.query.Param("kw2") String kw2,
        @org.springframework.data.repository.query.Param("kw3") String kw3,
        @org.springframework.data.repository.query.Param("kw4") String kw4,
        @org.springframework.data.repository.query.Param("kw5") String kw5);


    java.util.List<Recipe> findAllByNameIgnoreCase(String name);
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Recipe r LEFT JOIN FETCH r.recipeIngredients ri LEFT JOIN FETCH ri.ingredient WHERE r.totalPrice IS NULL")
    java.util.List<Recipe> findByTotalPriceIsNull();

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r WHERE r.image IS NULL OR r.image = '' OR r.image LIKE '%unsplash%' OR r.image LIKE '%splash%'")
    List<Recipe> findRecipesMissingImages();

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r WHERE LOWER(r.name) IN (SELECT LOWER(rd.name) FROM RecipeData rd)")
    List<Recipe> findRecipesExistingInData();

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r WHERE r.origin = 'EXPLORE' AND r.status = true AND LOWER(r.cuisine.name) = LOWER(:cuisine) AND r.image IS NOT NULL AND r.image != '' AND r.image NOT LIKE '%unsplash%' AND r.image NOT LIKE '%splash%' ORDER BY RANDOM()")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
    org.springframework.data.domain.Page<Recipe> findByCuisineWithImage(@org.springframework.data.repository.query.Param("cuisine") String cuisine, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r WHERE r.origin = 'EXPLORE' AND r.status = true AND LOWER(:category) IN (SELECT LOWER(c.name) FROM r.categories c) AND r.image IS NOT NULL AND r.image != '' AND r.image NOT LIKE '%unsplash%' AND r.image NOT LIKE '%splash%' ORDER BY RANDOM()")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
    org.springframework.data.domain.Page<Recipe> findByCategoryWithImage(@org.springframework.data.repository.query.Param("category") String category, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r WHERE " +
        "(:origin IS NULL OR r.origin = :origin) AND " +
        "(:name IS NULL OR :name = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
        "(:cuisine IS NULL OR :cuisine = '' OR r.cuisine.name = :cuisine) AND " +
        "(:category IS NULL OR :category = '' OR :category IN (SELECT c.name FROM r.categories c))")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "categories", "cuisine"})
    org.springframework.data.domain.Page<Recipe> findAdminRecipesWithFilters(
        @org.springframework.data.repository.query.Param("origin") com.cooked.backend.entity.RecipeOrigin origin,
        @org.springframework.data.repository.query.Param("name") String name,
        @org.springframework.data.repository.query.Param("cuisine") String cuisine,
        @org.springframework.data.repository.query.Param("category") String category,
        org.springframework.data.domain.Pageable pageable);
}
