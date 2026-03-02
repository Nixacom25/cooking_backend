package com.cooked.backend.dto.request;

import com.cooked.backend.entity.MealType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateMealPlanRequest {
    @NotNull(message = "Recipe ID is required")
    private UUID recipeId;

    @NotNull(message = "Planned date is required")
    private LocalDate plannedDate;

    @NotNull(message = "Meal type is required")
    private MealType mealType;
}
