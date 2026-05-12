package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RecipeService {
        RecipeResponse create(String userEmail, CreateRecipeRequest request);

        List<RecipeResponse> getMyRecipes(String userEmail);

        RecipeResponse getRecipe(UUID id, String userEmail);

        MessageResponse delete(UUID id, String userEmail);

        MessageResponse togglePublicVisibility(UUID id, String userEmail);

        org.springframework.data.domain.Page<RecipeResponse> getExploreRecipes(
                        String cuisine,
                        String category,
                        org.springframework.data.domain.Pageable pageable);

        MessageResponse toggleFavorite(UUID id, String userEmail);

        org.springframework.data.domain.Page<RecipeResponse> getFavoriteRecipes(String userEmail,
                        org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<com.cooked.backend.dto.response.CreatorResponse> getTopCreators(
                        org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<RecipeResponse> getPopularRecipes(String category, String userEmail,
                        org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<RecipeResponse> getRecentImports(String userEmail,
                        org.springframework.data.domain.Pageable pageable);
                        
        RecipeResponse validateSuggestedRecipe(UUID id, String userEmail);

        RecipeResponse importAndSaveAsSuggestion(String url, String userEmail);

        String getShareLink(UUID id, String userEmail);
        
        List<RecipeResponse> getHomeSuggestions(String userEmail);
        
        Map<String, Long> getExploreCuisines();
        
        Map<String, Long> getExploreCategories();
}
