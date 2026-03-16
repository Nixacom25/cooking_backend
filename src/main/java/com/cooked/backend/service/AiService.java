package com.cooked.backend.service;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiService {
    CreateRecipeRequest extractRecipeFromLink(String url, String email);

    AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email);

    List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email);
}
