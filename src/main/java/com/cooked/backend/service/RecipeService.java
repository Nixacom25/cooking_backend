package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeResponse;

import java.util.List;
import java.util.UUID;

public interface RecipeService {
    RecipeResponse create(String userEmail, CreateRecipeRequest request);

    List<RecipeResponse> getMyRecipes(String userEmail);

    RecipeResponse getRecipe(UUID id, String userEmail);

    MessageResponse delete(UUID id, String userEmail);
}
