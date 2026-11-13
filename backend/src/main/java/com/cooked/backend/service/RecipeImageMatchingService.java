package com.cooked.backend.service;

import java.util.UUID;

/**
 * Finds and attaches the closest dish-specific photo already in the
 * {@code image_library}. Leaves the image unset if no sufficiently reliable
 * match is found. Runs asynchronously so recipe generation isn't blocked on it.
 */
public interface RecipeImageMatchingService {
    void matchAndAssignImageAsync(UUID recipeId);

    /**
     * Same matching, now, on a recipe being saved: sets the best verified library photo and
     * returns true, or returns false (image left as is) when none matches.
     */
    boolean assignFromLibrary(com.cooked.backend.entity.Recipe recipe);
}
