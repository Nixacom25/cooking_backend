package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.dto.response.ScanResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.AiServiceException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiService;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.SubscriptionService;
import com.cooked.backend.exception.PaymentRequiredException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {
    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final CloudinaryService cloudinaryService;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    private void verifyAiAccess(User user) {
        if (!subscriptionService.hasAiAccess(user)) {
            throw new PaymentRequiredException("AI access requires a premium subscription or active trial period.");
        }
    }

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);

        try {
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                    lowerUrl.endsWith(".png") || lowerUrl.endsWith(".webp")) {
                String visionPrompt = String.format(
                        "Analyze this image of a dish and provide the complete detailed recipe. " +
                                "Source URL: %s. " +
                                "Dietary restrictions: Allergies: %s, Preferences: %s. " +
                                "Include prepTime and cookTime in minutes. " +
                                "Provide a deep preparation flow from prep to finish. " +
                                "IMPORTANT: Keep the recipe 'name' concise (max 60 characters). If the original name is too long, rename it to something short and catchy. " +
                                "Return ONLY raw JSON. " +
                                "Return JSON in this format: %s",
                        url,
                        String.join(", ", user.getAllergies()),
                        String.join(", ", user.getDietaryPreferences()),
                        RECIPE_JSON_FORMAT);

                String responseJson = sanitizeJson(callOpenAiVision(url, visionPrompt));
                CreateRecipeRequest req = objectMapper.readValue(responseJson, CreateRecipeRequest.class);
                req.setSourceUrl(url);
                return req;
            }

            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .timeout(20000)
                .get();
            
            doc.select("script, style, footer, nav, aside").remove();
            String pageText = String.format("Source URL: %s\nTitle: %s\nContent: %s", url, doc.title(), doc.body().text());
            if (pageText.length() > 25000) pageText = pageText.substring(0, 25000);

            String scrapingPrompt = String.format(
                    "CRITICAL TASK: Extract the EXACT recipe from the provided text for the URL: %s. " +
                            "Text Content: %s. " +
                            "User Allergies: %s, Preferences: %s. " +
                            "IMPORTANT: Many websites have sidebars with 'other recipes'. IGNORE THEM. Focus ONLY on the main recipe of the page. " +
                            "IMAGE EXTRACTION: If you cannot find a direct image URL for the dish in the text, use a high-quality relevant image URL from Unsplash (e.g., https://images.unsplash.com/photo-...) specifically for this dish: %s. " +
                            "If the text is insufficient, you may infer missing details to ensure a complete recipe, but DO NOT invent a different dish. " +
                            "Include prepTime and cookTime in minutes. " +
                            "IMPORTANT: Keep the recipe 'name' concise (max 60 characters). If the original name is too long, rename it to something short and catchy. " +
                            "Return ONLY raw JSON. " +
                            "Return JSON in this format: %s",
                    url,
                    pageText,
                    String.join(", ", user.getAllergies()),
                    String.join(", ", user.getDietaryPreferences()),
                    doc.title(),
                    RECIPE_JSON_FORMAT);

            String responseJson = sanitizeJson(callOpenAi("Extract recipe from link", scrapingPrompt, "gpt-4o"));
            CreateRecipeRequest req = objectMapper.readValue(responseJson, CreateRecipeRequest.class);
            req.setSourceUrl(url);
            return req;
        } catch (org.jsoup.HttpStatusException e) {
            if (e.getStatusCode() == 429) throw new AiServiceException("Website is blocking us. Please try scanning a screenshot.", "SCRAPING_BLOCKED");
            throw new AiServiceException("Failed to fetch recipe from link: HTTP " + e.getStatusCode(), "SCRAPING_HTTP_ERROR");
        } catch (Exception e) {
            log.error("AI Error extracting recipe from {}: {}", url, e.getMessage());
            throw new AiServiceException("Failed to extract recipe. Please ensure the link points to a valid recipe page and try again.", "IA_GPT_ERROR");
        }
    }

    private String sanitizeJson(String json) {
        if (json == null) return null;
        String sanitized = json.trim();
        if (sanitized.startsWith("```json")) sanitized = sanitized.substring(7);
        else if (sanitized.startsWith("```")) sanitized = sanitized.substring(3);
        if (sanitized.endsWith("```")) sanitized = sanitized.substring(0, sanitized.length() - 3);
        return sanitized.trim();
    }

    @Override
    public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);

        try {
            String imageUrl = cloudinaryService.upload(file);
            String detectionPrompt = String.format(
                    "Analyze this image and list ALL food ingredients you see. " +
                            "Dietary restrictions: Allergies: %s, Preferences: %s. " +
                            "Categorize them into 'allowed_ingredients' and 'restricted_ingredients'. " +
                            "IMPORTANT: Return ONLY raw JSON. No markdown code blocks. " +
                            "Format: {\"allowed_ingredients\": [{\"name\": \"name\", \"icon\": \"🥕\"}], \"restricted_ingredients\": [{\"name\": \"name\", \"icon\": \"🚫\", \"reason\": \"why\"}]}",
                    String.join(", ", user.getAllergies()),
                    String.join(", ", user.getDietaryPreferences()));

            String responseJson = sanitizeJson(callOpenAiVision(imageUrl, detectionPrompt));
            return objectMapper.readValue(responseJson, AiIngredientDetectionResponse.class);
        } catch (Exception e) {
            throw new AiServiceException("AI failed to detect ingredients. Please try again with a clearer photo.", "IA_DETECTION_ERROR");
        }
    }

    @Override
    public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email) {
        return generateRecipes(request, email, "gpt-4o");
    }

    public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email, String model) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);

        try {
            String allowedNames = String.join(", ", request.getIngredients());
            String generationPrompt = String.format(
                    "STRICT REQUIREMENT: Generate recipes using ONLY these ingredients: %s. " +
                            "You MAY assume the user has basic seasonings: salt, pepper, and cooking oil. " +
                            "DO NOT include any other ingredients (like spinach, butter, milk, flour, etc.) unless they are explicitly listed above. " +
                            "The recipes must be practical and achievable with ONLY the provided items. " +
                            "User Allergies: %s, Dietary Preferences: %s. " +
                            "User Goals: %s, Grocery Budget: %s. " +
                            "Generate between 6 and 10 distinct recipes. Provide high-quality Unsplash images for each. " +
                            "IMPORTANT: Keep the recipe 'name' concise (max 60 characters). " +
                            "IMPORTANT: Return ONLY raw JSON. " +
                            "Format: {\"recipes\": [%s]}",
                    allowedNames,
                    String.join(", ", user.getAllergies()),
                    String.join(", ", user.getDietaryPreferences()),
                    user.getOnboardingGoals() != null ? String.join(", ", user.getOnboardingGoals()) : "None",
                    user.getGroceryBudget() != null ? user.getGroceryBudget() : "Any",
                    RECIPE_JSON_FORMAT);

            String recipesJson = sanitizeJson(callOpenAi("Generate recipes", generationPrompt, model));
            JsonNode recipesNode = objectMapper.readTree(recipesJson);
            return objectMapper.convertValue(recipesNode.path("recipes"), new TypeReference<List<CreateRecipeRequest>>() {});
        } catch (Exception e) {
            throw new AiServiceException("AI failed to generate recipes. Please try again later.", "IA_GENERATION_ERROR");
        }
    }

    @Override
    public List<CreateRecipeRequest> generateInitialRecipes(User user, int count) {
        try {
            String generationPrompt = String.format(
                    "Create %d personalized recipes for a new user. " +
                            "Dietary Preferences: %s. " +
                            "Allergies to avoid: %s. " +
                            "Flavor DNA: %s. " +
                            "Cooking Skill: %s. " +
                            "Goals: %s. " +
                            "Budget: %s. " +
                            "Generate distinct, high-quality recipes. Provide beautiful Unsplash images for each. " +
                            "IMPORTANT: Keep the recipe 'name' concise (max 60 characters). " +
                            "IMPORTANT: Return ONLY raw JSON. " +
                            "Format: {\"recipes\": [%s]}",
                    count,
                    String.join(", ", user.getDietaryPreferences()),
                    String.join(", ", user.getAllergies()),
                    user.getFlavorDna() != null ? user.getFlavorDna().toString() : "Standard",
                    user.getCookingSkill() != null ? user.getCookingSkill() : "Intermediate",
                    user.getOnboardingGoals() != null ? String.join(", ", user.getOnboardingGoals()) : "None",
                    user.getGroceryBudget() != null ? user.getGroceryBudget() : "Any",
                    RECIPE_JSON_FORMAT);

            String recipesJson = sanitizeJson(callOpenAi("Generate initial recipes", generationPrompt));
            JsonNode recipesNode = objectMapper.readTree(recipesJson);
            return objectMapper.convertValue(recipesNode.path("recipes"), new TypeReference<List<CreateRecipeRequest>>() {});
        } catch (Exception e) {
            log.error("AI failed to generate initial recipes: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public ScanResponse scan(MultipartFile file, String email) {
        try {
            String imageUrl = cloudinaryService.upload(file);
            AiIngredientDetectionResponse detection = detectIngredients(file, email);
            
            List<String> allowedNames = detection.getAllowed_ingredients().stream()
                    .map(IngredientPayload::getName)
                    .toList();
            
            List<CreateRecipeRequest> recipes = generateRecipes(AiRecipeGenerationRequest.builder().ingredients(allowedNames).build(), email);

            return ScanResponse.builder()
                    .success(true)
                    .allowed_ingredients(detection.getAllowed_ingredients().stream()
                            .map(i -> {
                                IngredientPayload p = new IngredientPayload();
                                p.setName(i.getName());
                                p.setIcon(i.getIcon());
                                p.setQuantity("-");
                                return p;
                            }).toList())
                    .restricted_ingredients(detection.getRestricted_ingredients())
                    .image_url(imageUrl)
                    .recipes(recipes)
                    .build();

        } catch (Exception e) {
            throw new AiServiceException("Photo analysis failed. Please ensure food items are clearly visible.", "IA_SCAN_ERROR");
        }
    }

    @Override
    public ScanResponse scanTyped(List<String> ingredients, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);

        try {
            String categorizationPrompt = String.format(
                    "Take this list: %s. User Allergies: %s, Preferences: %s. " +
                            "Categorize into allowed/restricted. Return ONLY raw JSON. " +
                            "Format: {\"allowed_ingredients\": [{\"name\": \"name\", \"icon\": \"🥕\"}], \"restricted_ingredients\": [{\"name\": \"name\", \"icon\": \"🚫\", \"reason\": \"why\"}]}",
                    ingredients.toString(),
                    String.join(", ", user.getAllergies()),
                    String.join(", ", user.getDietaryPreferences()));

            String responseJson = sanitizeJson(callOpenAi("Categorize ingredients", categorizationPrompt, "gpt-4o-mini"));
            AiIngredientDetectionResponse categorization = objectMapper.readValue(responseJson, AiIngredientDetectionResponse.class);

            List<String> allowedNames = categorization.getAllowed_ingredients().stream()
                    .map(IngredientPayload::getName)
                    .toList();
            
            List<CreateRecipeRequest> recipes = generateRecipes(
                AiRecipeGenerationRequest.builder().ingredients(allowedNames).build(), 
                email, 
                "gpt-4o-mini"
            );

            return ScanResponse.builder()
                    .success(true)
                    .allowed_ingredients(categorization.getAllowed_ingredients().stream()
                            .map(i -> {
                                IngredientPayload p = new IngredientPayload();
                                p.setName(i.getName());
                                p.setIcon(i.getIcon());
                                p.setQuantity("-");
                                return p;
                            }).toList())
                    .restricted_ingredients(categorization.getRestricted_ingredients())
                    .recipes(recipes)
                    .build();
        } catch (Exception e) {
            log.error("Error in scanTyped: {}", e.getMessage());
            throw new AiServiceException("Failed to process ingredients. Please check your list.", "IA_TYPED_SCAN_ERROR");
        }
    }

    private static final String RECIPE_JSON_FORMAT = """
            {
              "name": "Recipe Name (Max 60 chars)",
              "prepTime": 15,
              "cookTime": 30,
              "kcal": 500,
              "servings": 4,
              "image": "https://images.unsplash.com/photo-...",
              "ingredients": [{"name": "name", "quantity": "2 cups", "icon": "🥕", "price": 1.50}],
              "steps": [
                "Detailed preparation: (Wash, peel, cut ingredients, marinate if needed)",
                "Main cooking steps: (Heat, combine, timing, specific techniques)",
                "Finishing touches: (Seasoning, plating, garnish)"
              ],
              "tips": "Pro tips for flavor, storage, or substitutions."
            }""";

    private String callOpenAiVision(String imageUrl, String prompt) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", prompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))))),
                "response_format", Map.of("type", "json_object"),
                "max_tokens", 1500,
                "temperature", 0.1);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        }
        throw new AiServiceException("AI Analysis failed. Please try again.", "IA_VISION_FAILURE");
    }

    private String callOpenAi(String title, String prompt, String model) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        Map<String, Object> requestBody = Map.of(
                "model", model != null ? model : "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a master chef assistant. Output raw JSON ONLY."),
                        Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object"),
                "max_tokens", 2500,
                "temperature", 0.3);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        }
        throw new AiServiceException("AI Generation failed. Please try again.", "IA_GPT_FAILURE");
    }

    private String callOpenAi(String title, String prompt) throws JsonProcessingException {
        return callOpenAi(title, prompt, "gpt-4o-mini");
    }

    @Override
    public List<Map<String, String>> searchWeb(String query, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);
        
        try {
            String searchUrl = "https://html.duckduckgo.com/html/?q="
                    + java.net.URLEncoder.encode(query + " recipe", "UTF-8");
            Document doc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").get();

            return doc.select(".result").stream()
                    .limit(10)
                    .map(element -> {
                        String title = element.select(".result__title").text();
                        String snippet = element.select(".result__snippet").text();
                        String url = element.select(".result__a").attr("href");
                        if (url.startsWith("//")) url = "https:" + url;
                        return Map.of("title", title, "url", url, "snippet", snippet);
                    })
                    .filter(m -> !m.get("url").isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new AiServiceException("Web search failed.", "WEB_SEARCH_ERROR");
        }
    }

    @Override
    public List<String> generateTrendingDishes() {
        try {
            String prompt = "Generate 6 popular and trending dish names from around the world. " +
                    "IMPORTANT: Return ONLY raw JSON in this format: {\"trending\": [\"Dish 1\", \"Dish 2\", \"Dish 3\", \"Dish 4\", \"Dish 5\", \"Dish 6\"]}";
                    
            String responseJson = sanitizeJson(callOpenAi("Generate trending dishes", prompt, "gpt-4o-mini"));
            JsonNode root = objectMapper.readTree(responseJson);
            return objectMapper.convertValue(root.path("trending"), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to generate trending dishes: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public Map<String, Double> estimateIngredientPrices(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        
        try {
            String prompt = String.format(
                    "Estimate the average USD price for the following ingredients in a typical US grocery store. " +
                    "Return ONLY raw JSON in this format: {\"prices\": {\"ingredient1\": 1.50, \"ingredient2\": 3.00}}. " +
                    "Ingredients: %s",
                    String.join(", ", ingredients));
            
            String responseJson = sanitizeJson(callOpenAi("Estimate ingredient prices", prompt, "gpt-4o-mini"));
            JsonNode root = objectMapper.readTree(responseJson);
            return objectMapper.convertValue(root.path("prices"), new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.error("Failed to estimate ingredient prices: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }
}
