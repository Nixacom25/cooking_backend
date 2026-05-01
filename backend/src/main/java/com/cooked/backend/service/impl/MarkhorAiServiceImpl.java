package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Primary
@Slf4j
public class MarkhorAiServiceImpl implements AiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final com.cooked.backend.repository.RecipeDataRepository recipeDataRepository;
    private final AiService legacyAiService;

    public MarkhorAiServiceImpl(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            com.cooked.backend.repository.RecipeDataRepository recipeDataRepository,
            @Qualifier("aiServiceImpl") AiService legacyAiService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.recipeDataRepository = recipeDataRepository;
        this.legacyAiService = legacyAiService;
    }

    @Value("${AI_API_BASE_URL:https://recipe.markhorsystems.com}")
    private String baseUrl;

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("url", url);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl + "/api/extract",
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> resBody = response.getBody();
                if (Boolean.TRUE.equals(resBody.get("success"))) {
                    Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                    if (data == null) {
                        throw new BadRequestException("Échec de l'extraction : Champ 'data' manquant");
                    }

                    Map<String, Object> aiResponse = (Map<String, Object>) data.get("recipe");
                    if (aiResponse == null || !"success".equals(aiResponse.get("status"))) {
                        String msg = aiResponse != null ? (String) aiResponse.get("fallback_message") : "Erreur inconnue";
                        throw new BadRequestException("Échec de l'extraction : " + msg);
                    }

                    Map<String, Object> recipeMap = (Map<String, Object>) aiResponse.get("recipe");
                    if (recipeMap == null) {
                        throw new BadRequestException("Échec de l'extraction : Données de recette manquantes");
                    }

                    // Enrich recipe with platform image if not present
                    if (recipeMap.get("image") == null || ((String) recipeMap.get("image")).isEmpty()) {
                        recipeMap.put("image", data.get("image"));
                    }

                    // Ensure origin and sourceUrl are set
                    recipeMap.put("origin", "IMPORT");
                    recipeMap.put("sourceUrl", url);

                    return objectMapper.convertValue(recipeMap, CreateRecipeRequest.class);
                }
            }

            throw new BadRequestException("Échec de l'extraction de la recette par l'IA.");

        } catch (Exception e) {
            log.error("Erreur lors de l'extraction de la recette depuis le lien : {}", e.getMessage());
            throw new BadRequestException("Impossible d'extraire la recette : " + e.getMessage());
        }
    }

    @Override
    public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
        // Not used anymore as per user request
        return null;
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

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Merge incoming request with user preferences if not provided
            if (request.getUser_preferences() == null) {
                request.setUser_preferences(mapUserToPreferences(user));
            }

            HttpEntity<AiRecipeGenerationRequest> requestEntity = new HttpEntity<>(request, headers);

            ResponseEntity<com.cooked.backend.dto.response.ScanResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/recipes/generate",
                    requestEntity,
                    com.cooked.backend.dto.response.ScanResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().isSuccess()) {
                List<CreateRecipeRequest> recipes = response.getBody().getRecipes();
                if (recipes != null) {
                    for (CreateRecipeRequest r : recipes) r.setOrigin("MANUAL");
                }
                return recipes;
            }
        } catch (Exception e) {
            log.error("Error generating recipes via Node.js: {}", e.getMessage());
        }
        return legacyAiService.generateRecipes(request, email);
    }

    @Override
    public List<CreateRecipeRequest> generateInitialRecipes(User user, int count) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Get a pool of recipes from local data
            List<com.cooked.backend.entity.RecipeData> rawPool = recipeDataRepository.findRandomRecipes(30);
            List<String> namesPool = rawPool.stream()
                    .map(com.cooked.backend.entity.RecipeData::getName)
                    .collect(java.util.stream.Collectors.toList());

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("ingredients", java.util.Collections.emptyList());
            body.put("recipe_pool", namesPool); 
            body.put("user_preferences", mapUserToPreferences(user));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<com.cooked.backend.dto.response.ScanResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/recipes/suggest",
                    requestEntity,
                    com.cooked.backend.dto.response.ScanResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().isSuccess()) {
                List<CreateRecipeRequest> recipes = response.getBody().getRecipes();
                if (recipes != null) {
                    for (CreateRecipeRequest r : recipes) {
                        r.setOrigin("ONBOARDING");
                        // Link back the original image from the pool
                        rawPool.stream()
                            .filter(p -> p.getName().equalsIgnoreCase(r.getName()))
                            .findFirst()
                            .ifPresent(p -> r.setImage(p.getImageUrl()));
                    }
                }
                return recipes;
            }
        } catch (Exception e) {
            log.error("Error generating initial recipes via Node.js with pool: {}", e.getMessage());
        }
        return legacyAiService.generateInitialRecipes(user, count);
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
            
            String customInstructions = "Format the recipe with: " +
                "1. 'ingredients': list of ingredients. " +
                "2. 'equipment': list of ALL kitchen tools/equipment. " +
                "3. 'steps': list of steps. " +
                "Example format: {\"name\": \"...\", \"ingredients\": [...], \"equipment\": [\"pan\", \"pot\"], \"steps\": [\"Step 1: ...\"]}";
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

            AiRecipeGenerationRequest.UserPreferencesPayload prefs = mapUserToPreferences(user);
            body.put("user_preferences", prefs);
            
            String customInstructions = "Format the recipe with: " +
                "1. 'ingredients': list of ingredients. " +
                "2. 'equipment': list of ALL kitchen tools/equipment. " +
                "3. 'steps': list of steps. " +
                "Example format: {\"name\": \"...\", \"ingredients\": [...], \"equipment\": [\"pan\", \"pot\"], \"steps\": [\"Step 1: ...\"]}";
            body.put("custom_instructions", customInstructions);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<com.cooked.backend.dto.response.ScanResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/recipes/generate",
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
        try {
            String url = baseUrl + "/api/search?q=" + query;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (Boolean.TRUE.equals(body.get("success"))) {
                    return (List<Map<String, String>>) body.get("data");
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la recherche web : {}", e.getMessage());
        }
        return List.of();
    }

    @Override
    public List<String> generateTrendingDishes() {
        try {
            String url = baseUrl + "/api/recipes/trending";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (Boolean.TRUE.equals(body.get("success"))) {
                    return (List<String>) body.get("trending");
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la génération des tendances : {}", e.getMessage());
        }
        return legacyAiService.generateTrendingDishes();
    }
}
