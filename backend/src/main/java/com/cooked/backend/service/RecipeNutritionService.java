package com.cooked.backend.service;

import com.cooked.backend.dto.response.NutritionEstimateResponse;

import java.util.UUID;

/** Nutrition per serving estimated by the AI from a recipe's ingredients. */
public interface RecipeNutritionService {

    /**
     * Estimates and saves protein, carbs, fat, fiber and sodium per serving.
     * kcal is filled only when the recipe has none (an existing value is kept).
     */
    NutritionEstimateResponse estimate(UUID recipeId);
}
