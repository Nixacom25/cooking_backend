package com.cooked.backend.service;

import com.cooked.backend.entity.CategoryType;
import com.cooked.backend.entity.RecipeCategory;
import java.util.Map;

public interface TaxonomyService {
    RecipeCategory getOrCreateCategory(String name, CategoryType type);
    void migrateExistingRecipes();
    
    Map<String, String> getCategoryImages();
    Map<String, String> getCuisineImages();
}
