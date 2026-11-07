package com.cooked.backend.service;

import java.util.UUID;

/**
 * Finds and attaches a real, verified photo for a recipe: first by tag
 * overlap against the {@code image_library}, then by external search + vision
 * verification. Leaves the image unset if no sufficiently reliable match is
 * found. Runs asynchronously so recipe generation isn't blocked on it.
 */
public interface RecipeImageMatchingService {
    void matchAndAssignImageAsync(UUID recipeId);
}
