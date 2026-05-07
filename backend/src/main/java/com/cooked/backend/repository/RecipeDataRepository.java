package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeDataRepository extends JpaRepository<RecipeData, Long> {
    boolean existsByName(String name);
    List<RecipeData> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM recipe_data ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<RecipeData> findRandomRecipes(@org.springframework.data.repository.query.Param("limit") int limit);
    List<RecipeData> findByNameContainingIgnoreCase(String name);

    @org.springframework.data.jpa.repository.Query("SELECT rd FROM RecipeData rd WHERE LOWER(rd.name) IN (SELECT LOWER(r.name) FROM Recipe r)")
    List<RecipeData> findRecipesPresentInRecipes();
}
