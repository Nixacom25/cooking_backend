package com.cooked.backend.dto.response;

import java.util.List;

/**
 * Structured tags an approved AI photo is stored with in the image library, so
 * future recipes reuse it by dish characteristics, never by title alone.
 */
public record AiRecipeImageTags(
        String dishType,
        String cuisine,
        String protein,
        List<String> mainIngredients,
        String mealType,
        String cookingMethod,
        List<String> dietaryTags,
        String visualStyle,
        /** 1-5. */
        Integer qualityScore) {
}
