package com.cooked.backend.service;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.dto.response.ScanResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface AiService {
    CreateRecipeRequest extractRecipeFromLink(String url, String email);

    AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email);

    List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email);

    List<CreateRecipeRequest> generateInitialRecipes(com.cooked.backend.entity.User user, int count);

    ScanResponse scan(MultipartFile file, String email);

    ScanResponse scanTyped(List<String> ingredients, String email);

    List<Map<String, String>> searchWeb(String query, String email);

    List<String> generateTrendingDishes();
}
