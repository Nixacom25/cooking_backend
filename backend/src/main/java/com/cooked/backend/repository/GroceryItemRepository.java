package com.cooked.backend.repository;

import com.cooked.backend.entity.GroceryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface GroceryItemRepository extends JpaRepository<GroceryItem, UUID> {
    List<GroceryItem> findAllByUserId(UUID userId);

    List<GroceryItem> findAllByUserIdAndPlannedDate(UUID userId, LocalDate plannedDate);

    java.util.Optional<GroceryItem> findByUserIdAndIngredientIdAndRecipeIdAndPlannedDate(
            UUID userId, UUID ingredientId, UUID recipeId, LocalDate plannedDate);

    java.util.Optional<GroceryItem> findByUserIdAndIngredientIdAndPlannedDate(
            UUID userId, UUID ingredientId, LocalDate plannedDate);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE GroceryItem gi SET gi.recipe = :twin WHERE gi.recipe = :oldRecipe")
    void repointRecipe(@org.springframework.data.repository.query.Param("oldRecipe") com.cooked.backend.entity.Recipe oldRecipe, 
                       @org.springframework.data.repository.query.Param("twin") com.cooked.backend.entity.Recipe twin);
}
