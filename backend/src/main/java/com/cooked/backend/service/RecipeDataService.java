package com.cooked.backend.service;

import com.cooked.backend.entity.RecipeData;
import com.cooked.backend.entity.Recipe;

public interface RecipeDataService {
    RecipeData create(String name, org.springframework.web.multipart.MultipartFile image);
    java.util.List<RecipeData> bulkCreate(java.util.List<String> names, java.util.List<org.springframework.web.multipart.MultipartFile> images);
    java.util.List<RecipeData> getAll();
    RecipeData getById(Long id);
    void delete(Long id);
    RecipeData update(Long id, String name, org.springframework.web.multipart.MultipartFile image);
    void deleteMultiple(java.util.List<Long> ids);
    java.util.Map<String, Object> bulkUpdateImages(java.util.List<org.springframework.web.multipart.MultipartFile> files);
    java.util.List<java.util.Map<String, Object>> getRecipesMissingImages();
    java.util.List<java.util.Map<String, Object>> getRecipesExistingInData();
}
