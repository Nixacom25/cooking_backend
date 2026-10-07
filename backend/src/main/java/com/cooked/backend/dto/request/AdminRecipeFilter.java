package com.cooked.backend.dto.request;

import com.cooked.backend.entity.RecipeOrigin;

import java.util.UUID;

/**
 * Filters of the admin Recipes table (all optional).
 *
 * @param origin     where the recipe comes from
 * @param name       name contains (case-insensitive)
 * @param cuisineId  cuisine category id
 * @param visibility PUBLIC, PRIVATE or DELETED
 * @param hasImage   true = with an image, false = without
 */
public record AdminRecipeFilter(RecipeOrigin origin, String name, UUID cuisineId, String visibility, Boolean hasImage) {

    /** True when only origin / name are set (the indexed legacy queries handle those). */
    public boolean basic() {
        return cuisineId == null && (visibility == null || visibility.isBlank()) && hasImage == null;
    }
}
