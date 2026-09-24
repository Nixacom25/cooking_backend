package com.cooked.backend.service;

import com.cooked.backend.entity.CategoryType;
import com.cooked.backend.entity.RecipeCategory;
import java.util.Map;

public interface TaxonomyService {
    RecipeCategory getOrCreateCategory(String name, CategoryType type);

    /**
     * Resolves a raw category/cuisine name to its canonical taxonomy name
     * (e.g. "Breakfast" -&gt; "Healthy Breakfasts"), the same aliasing
     * {@link #getOrCreateCategory} applies when tagging a recipe. Callers
     * that create/rename a {@link RecipeCategory} directly (e.g. the admin
     * dashboard) must run names through this first, or an admin-created
     * category can end up on a different row than the one recipes actually
     * get linked to - it would show "active" in the admin list while never
     * receiving any recipes.
     */
    String normalizeCategoryName(String name, CategoryType type);

    void migrateExistingRecipes();
    
    Map<String, String> getCategoryImages();
    Map<String, String> getCuisineImages();
    void mergeDuplicateTaxonomies();
}
