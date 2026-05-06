package com.cooked.backend.service;

import com.cooked.backend.entity.RecipeData;
import com.cooked.backend.entity.Recipe;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface RecipeDataService {
    RecipeData create(String name, MultipartFile image);
    List<RecipeData> bulkCreate(List<String> names, List<MultipartFile> images);
    List<RecipeData> getAll();
    RecipeData getById(Long id);
    void delete(Long id);
    RecipeData update(Long id, String name, MultipartFile image);
    void deleteMultiple(List<Long> ids);
    java.util.Map<String, Object> bulkUpdateImages(List<MultipartFile> files);
    List<Recipe> getRecipesMissingImages();
    List<Recipe> getRecipesExistingInData();
}
