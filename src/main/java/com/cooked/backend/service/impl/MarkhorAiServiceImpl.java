package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.dto.response.AiRecipeListResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarkhorAiServiceImpl implements AiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Value("${ai.api.base-url:https://recipe.markhorsystems.com}")
    private String baseUrl;

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
        // This is not supported by Markhor API yet, would typically fallback to OpenAI
        // For now, throwing not implemented or using existing logic if possible
        throw new UnsupportedOperationException("Link extraction not supported by Markhor AI yet.");
    }

    @Override
    public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Image part
            ByteArrayResource imageResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("image", imageResource);

            // User preferences part (JSON-stringified)
            AiRecipeGenerationRequest.UserPreferencesPayload prefs = mapUserToPreferences(user);
            body.add("user_preferences", objectMapper.writeValueAsString(prefs));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<AiIngredientDetectionResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/ingredients/detect",
                    requestEntity,
                    AiIngredientDetectionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new BadRequestException("Failed to detect ingredients from AI.");

        } catch (IOException e) {
            throw new BadRequestException("Could not process image: " + e.getMessage());
        }
    }

    private AiRecipeGenerationRequest.UserPreferencesPayload mapUserToPreferences(User user) {
        return AiRecipeGenerationRequest.UserPreferencesPayload.builder()
                .allergies(user.getAllergies())
                .preferences(user.getDietaryPreferences())
                .dislikes(user.getFoodDislikes())
                .cuisines_love(user.getFavoriteCuisines())
                .kitchen_tools(user.getKitchenAppliances())
                .cooking_goals(user.getOnboardingGoals())
                .DNA(user.getFlavorDna())
                .skill_level(mapSkillLevel(user.getCookingSkill()))
                .time_minutes(user.getCookingTimePreference())
                .servings(null) // Not explicitly in User entity yet, or could be mapped if found
                .build();
    }

    private String mapSkillLevel(String skill) {
        if (skill == null) return "HomeCook";
        String s = skill.toLowerCase();
        if (s.contains("begin")) return "total_beginer";
        if (s.contains("semi") || s.contains("advanced")) return "Advanced Semi Pro";
        if (s.contains("confident")) return "ConfidentCook";
        return "HomeCook";
    }

    @Override
    public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (request.getUser_preferences() == null) {
            request.setUser_preferences(mapUserToPreferences(user));
        }

        // Standardize output format as per user requirements
        request.setCustom_instructions(
            "Format the recipe with: " +
            "1. 'ingredients': the list of ingredients. " +
            "2. 'equipment': a comprehensive list of all kitchen tools and equipment necessary for the preparation of this recipe. " +
            "3. 'steps': each step MUST follow the format 'Step X: Title\\nDescription'. " +
            "Include heat levels, prep/cook instructions, and finishing steps."
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AiRecipeGenerationRequest> requestEntity = new HttpEntity<>(request, headers);

        ResponseEntity<AiRecipeListResponse> response = restTemplate.postForEntity(
                baseUrl + "/api/recipes/generate",
                requestEntity,
                AiRecipeListResponse.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().isSuccess()) {
            List<CreateRecipeRequest> recipes = response.getBody().getRecipes();
            // Origin will be set by caller or defaults to SCAN if coming from regular generation
            return recipes;
        }

        return Collections.emptyList();
    }

    @Override
    public List<CreateRecipeRequest> generateInitialRecipes(User user, int count) {
        // Construct a request that asks for 'count' recipes based on mapped preferences
        AiRecipeGenerationRequest request = AiRecipeGenerationRequest.builder()
                .ingredients(Collections.emptyList()) // General generation
                .user_preferences(mapUserToPreferences(user))
                .build();
        
        // Markhor API typically returns multiple recipes, we'll just use the default set or request multiple
        List<CreateRecipeRequest> recipes = generateRecipes(request, user.getEmail());
        for (CreateRecipeRequest r : recipes) {
            r.setOrigin("SUGGESTED");
        }
        return recipes;
    }

    @Override
    public com.cooked.backend.dto.response.ScanResponse scan(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Image part
            ByteArrayResource imageResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("image", imageResource);

            // User preferences part (JSON-stringified)
            AiRecipeGenerationRequest.UserPreferencesPayload prefs = mapUserToPreferences(user);
            body.add("user_preferences", objectMapper.writeValueAsString(prefs));
            body.add("user_id", user.getId().toString());
            
            String customInstructions = "Format the recipe with: " +
                "1. 'ingredients': the list of ingredients. " +
                "2. 'equipment': a comprehensive list of all kitchen tools and equipment necessary for the preparation of this recipe. " +
                "3. 'steps': each step MUST follow the format 'Step X: Title\\nDescription'. " +
                "Include heat levels, prep/cook instructions, and finishing steps.";
            body.add("custom_instructions", customInstructions);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<com.cooked.backend.dto.response.ScanResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/analyze",
                    requestEntity,
                    com.cooked.backend.dto.response.ScanResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().isSuccess()) {
                com.cooked.backend.dto.response.ScanResponse scanRes = response.getBody();
                if (scanRes != null && scanRes.getRecipes() != null) {
                    for (CreateRecipeRequest r : scanRes.getRecipes()) {
                        r.setOrigin("SCAN");
                    }
                }
                return scanRes;
            }

            throw new BadRequestException("Failed to analyze image/recipes from AI.");

        } catch (IOException e) {
            throw new BadRequestException("Could not process image: " + e.getMessage());
        }
    }

    @Override
    public com.cooked.backend.dto.response.ScanResponse scanTyped(List<String> ingredients, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("ingredients", ingredients);
            body.put("user_preferences", mapUserToPreferences(user));
            body.put("user_id", user.getId().toString());
            
            String customInstructions = "Format the recipe with: " +
                "1. 'ingredients': the list of ingredients. " +
                "2. 'equipment': a comprehensive list of all kitchen tools and equipment necessary for the preparation of this recipe. " +
                "3. 'steps': each step MUST follow the format 'Step X: Title\\nDescription'. " +
                "Include heat levels, prep/cook instructions, and finishing steps.";
            body.put("custom_instructions", customInstructions);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<com.cooked.backend.dto.response.ScanResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/analyze",
                    requestEntity,
                    com.cooked.backend.dto.response.ScanResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().isSuccess()) {
                com.cooked.backend.dto.response.ScanResponse scanRes = response.getBody();
                if (scanRes != null && scanRes.getRecipes() != null) {
                    for (CreateRecipeRequest r : scanRes.getRecipes()) {
                        r.setOrigin("SCAN");
                    }
                }
                return scanRes;
            }

            throw new BadRequestException("Failed to analyze ingredients/recipes from AI.");

        } catch (Exception e) {
            throw new BadRequestException("Could not process typed analyze: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, String>> searchWeb(String query) {
        throw new UnsupportedOperationException("Web search not supported by Markhor AI yet.");
    }

    @Override
    public List<String> generateTrendingDishes() {
        return List.of("Sushi", "Tacos", "Pizza", "Pad Thai", "Croissant", "Butter Chicken");
    }
}
