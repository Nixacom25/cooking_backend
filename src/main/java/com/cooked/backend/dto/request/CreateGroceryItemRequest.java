package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateGroceryItemRequest {
    @NotBlank(message = "Ingredient name is required")
    private String ingredientName;

    private String ingredientIcon;

    @NotBlank(message = "Quantity is required")
    private String quantity;

    // Optional fields
    private UUID recipeId;
    private LocalDate plannedDate;
}
