package com.cooked.backend.dto.response;

/** Per-serving nutrition estimated from the ingredients and saved on the recipe. */
public record NutritionEstimateResponse(Integer kcal, Double proteinG, Double carbsG, Double fatG, Double fiberG, Integer sodiumMg,
                                        boolean kcalUpdated) {
}
