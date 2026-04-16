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
}
