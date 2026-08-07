package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeStatsResponse {
    private long totalRecipes;
    private long unmodifiedRecipes;
    private long assignedRecipes;
    private long unassignedRecipes;
    private long deletedRecipes;
    private long modifiedRecipes;
    private long pendingValidationRecipes;
    private long validatedRecipes;
    private long needsCorrectionRecipes;
    private long inProgressRecipes;
    private long processedToday;
}
