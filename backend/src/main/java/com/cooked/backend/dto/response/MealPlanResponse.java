package com.cooked.backend.dto.response;

import com.cooked.backend.entity.MealType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MealPlanResponse {
    private UUID id;
    private RecipeResponse recipe;
    private LocalDate plannedDate;
    private MealType mealType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
