package com.cooked.backend.service;

import java.util.UUID;

/**
 * Finds and attaches a real, verified photo for a recipe: first by tag
 * overlap against the {@code image_library}, falling back to an external
 * search + vision verification, and finally to a generic category/cuisine
 * image. Never forces a low-confidence match. Runs asynchronously so recipe
 * generation isn't blocked on it.
 */
public interface RecipeImageMatchingService {
    void matchAndAssignImageAsync(UUID recipeId);
}
