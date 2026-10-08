package com.cooked.backend.repository;

import com.cooked.backend.entity.Ingredient;
import com.cooked.backend.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {
    
    @Query("SELECT ri.ingredient FROM RecipeIngredient ri " +
           "WHERE ri.recipe.user.id = :userId " +
           "GROUP BY ri.ingredient " +
           "ORDER BY MAX(ri.createdAt) DESC")
    List<Ingredient> findRecentIngredientsByUserId(@Param("userId") UUID userId, org.springframework.data.domain.Pageable pageable);

    /** Ingredient names by number of recipes using them, as rows [name, recipes] (Not-in-catalog backfill). */
    @Query("SELECT ri.ingredient.name, COUNT(DISTINCT ri.recipe.id) FROM RecipeIngredient ri " +
           "GROUP BY ri.ingredient.name ORDER BY COUNT(DISTINCT ri.recipe.id) DESC")
    List<Object[]> mostUsedNames(org.springframework.data.domain.Pageable pageable);
}
