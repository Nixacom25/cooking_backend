package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GroceryItemResponse {
    private UUID id;
    private IngredientResponse ingredient;
    private UUID recipeId; // Null if manual
    private String quantity;
    private Boolean isBought;
    private LocalDate plannedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
