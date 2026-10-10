package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.NutritionEstimateResponse;

import java.util.List;
import java.util.UUID;

/** Nutrition per serving estimated by the AI from a recipe's ingredients. */
public interface RecipeNutritionService {

    /**
     * Estimates and saves protein, carbs, fat, fiber and sodium per serving.
     * kcal is filled only when the recipe has none (an existing value is kept).
     */
    NutritionEstimateResponse estimate(UUID recipeId);

    /**
     * Fills kcal per serving on recipes that have none (null or 0) before they
     * are saved, from their ingredients, in one AI call. Best effort: never
     * throws, leaves kcal unset if the AI can't answer.
     */
    void fillMissingKcal(List<CreateRecipeRequest> recipes);
}
