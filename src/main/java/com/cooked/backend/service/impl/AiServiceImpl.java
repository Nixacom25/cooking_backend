package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.dto.response.ScanResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiService;
import com.cooked.backend.service.CloudinaryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
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
@Primary
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

        @Value("${openai.api.key}")
        private String openAiApiKey;

        private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
        private final RestTemplate restTemplate;
        private final ObjectMapper objectMapper;

        @Qualifier("markhorAiServiceImpl")
        private final AiService markhorAiService;

        private final CloudinaryService cloudinaryService;
        private final UserRepository userRepository;

        @Override
        public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new BadRequestException("User not found"));

                try {
                        // 1. Check if the URL is a direct image link
                        String lowerUrl = url.toLowerCase();
                        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                                        lowerUrl.endsWith(".png") || lowerUrl.endsWith(".webp")) {
                                String visionPrompt = String.format(
                                                "Analyze this image of a dish and provide the recipe. " +
                                                                "Dietary restrictions: Allergies: %s, Preferences: %s. "
                                                                +
                                                                "Return ONLY valid JSON in this format: %s",
                                                String.join(", ", user.getAllergies()),
                                                String.join(", ", user.getDietaryPreferences()),
                                                RECIPE_JSON_FORMAT);

                                String responseJson = callOpenAiVision(url, visionPrompt);
                                CreateRecipeRequest req = objectMapper.readValue(responseJson,
                                                CreateRecipeRequest.class);
                                req.setSourceUrl(url);
                                return req;
                        }

                        // 2. Otherwise, treat as a webpage (Blog/Social Media)
                        Document doc = Jsoup.connect(url)
                                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        .header("Accept-Language", "en-US,en;q=0.9")
                                        .header("Accept",
                                                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                                        .referrer("https://www.google.com/")
                                        .timeout(10000)
                                        .get();

                        // Try to get meta information if possible
                        String title = doc.title();
                        String description = doc.select("meta[name=description]").attr("content");
                        String ogDescription = doc.select("meta[property=og:description]").attr("content");
                        String twitterDescription = doc.select("meta[name=twitter:description]").attr("content");
                        String itemPropDescription = doc.select("meta[itemprop=description]").attr("content");

                        // Prefer most specific descriptions
                        String bestDescription = description;
                        if (bestDescription.isEmpty() || bestDescription.length() < 20)
                                bestDescription = ogDescription;
                        if (bestDescription.isEmpty() || bestDescription.length() < 20)
                                bestDescription = twitterDescription;
                        if (bestDescription.isEmpty() || bestDescription.length() < 20)
                                bestDescription = itemPropDescription;

                        String pageText = String.format("Title: %s\nDescription: %s\nContent: %s",
                                        title, bestDescription, doc.body().text());

                        if (pageText.length() > 10000) {
                                pageText = pageText.substring(0, 10000);
                        }

                        String scrapingPrompt = String.format(
                                        "Extract the recipe from this text: %s. " +
                                                        "User Allergies: %s, Preferences: %s. " +
                                                        "Provide a high-quality Unsplash image URL for the dish in the 'image' field. "
                                                        +
                                                        "Return ONLY valid JSON in this format: %s",
                                        pageText,
                                        String.join(", ", user.getAllergies()),
                                        String.join(", ", user.getDietaryPreferences()),
                                        RECIPE_JSON_FORMAT);

                        String responseJson = callOpenAi("Extract recipe from link", scrapingPrompt);
                        CreateRecipeRequest req = objectMapper.readValue(responseJson, CreateRecipeRequest.class);
                        req.setSourceUrl(url);
                        return req;
                } catch (org.jsoup.HttpStatusException e) {
                        if (e.getStatusCode() == 401 || e.getStatusCode() == 402 || e.getStatusCode() == 403
                                        || e.getStatusCode() == 429) {
                                throw new BadRequestException(
                                                "This website actively blocks automated importing. Please take a screenshot of the recipe and use the 'Scan' tab instead.");
                        }
                        throw new BadRequestException(
                                        "Could not process the provided URL: HTTP Error " + e.getStatusCode());
                } catch (IOException e) {
                        throw new BadRequestException("Could not fetch the URL. Please verify the link is accessible.");
                }
        }

        @Override
        public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
                // Delegate to Markhor AI as requested
                return markhorAiService.detectIngredients(file, email);
        }

        @Override
        public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email) {
                // Delegate to Markhor AI as requested
                return markhorAiService.generateRecipes(request, email);
        }

        @Override
        public ScanResponse scan(MultipartFile file, String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new BadRequestException("User not found"));

                try {
                        // 1. Upload to Cloudinary
                        System.out.println("SCAN_LOG: Starting upload to Cloudinary... Size: " + file.getSize() + " bytes");
                        String imageUrl = cloudinaryService.upload(file);
                        System.out.println("SCAN_LOG: Upload successful. Image URL: " + imageUrl);

                        // 2. Detect Ingredients using GPT-4o Vision
                        String detectionPrompt = String.format(
                                        "Analyze this image and list ALL food ingredients you see. " +
                                                        "Dietary restrictions: Allergies: %s, Preferences: %s. " +
                                                        "Categorize them into 'allowed_ingredients' (safe) and 'restricted_ingredients' (unsafe). "
                                                        +
                                                        "Return ONLY valid JSON in this format: " +
                                                        "{\"allowed_ingredients\": [{\"name\": \"ingredient name\", \"icon\": \"🥕\"}], \"restricted_ingredients\": [{\"name\": \"ingredient name\", \"icon\": \"🚫\", \"reason\": \"why\"}]}",
                                        String.join(", ", user.getAllergies()),
                                        String.join(", ", user.getDietaryPreferences()));

                        System.out.println("SCAN_LOG: Calling OpenAI Vision for ingredient detection...");
                        String detectionJson = callOpenAiVision(imageUrl, detectionPrompt);
                        System.out.println("SCAN_LOG: Detection response: " + (detectionJson.length() > 200 ? detectionJson.substring(0, 200) + "..." : detectionJson));
                        JsonNode detectionNode = objectMapper.readTree(detectionJson);

                        List<IngredientPayload> allowedIngredients = objectMapper.convertValue(
                                        detectionNode.path("allowed_ingredients"),
                                        new TypeReference<List<IngredientPayload>>() {
                                        });
                        List<IngredientPayload> restrictedIngredients = objectMapper.convertValue(
                                        detectionNode.path("restricted_ingredients"),
                                        new TypeReference<List<IngredientPayload>>() {
                                        });

                        // Null checks to prevent NPE
                        if (allowedIngredients == null) allowedIngredients = new java.util.ArrayList<>();
                        if (restrictedIngredients == null) restrictedIngredients = new java.util.ArrayList<>();

                        if (allowedIngredients.isEmpty()) {
                                throw new BadRequestException("No ingredients were detected in this image. Please ensure the food items are clearly visible and try again.");
                        }

                        // 3. Generate between 6 and 10 Recipes using GPT-4o-mini
                        String allowedNames = allowedIngredients.stream().map(IngredientPayload::getName).toList()
                                        .toString();
                        String generationPrompt = String.format(
                                        "Based on these ingredients: %s. " +
                                                        "User Allergies: %s, Preferences: %s. " +
                                                        "Generate between 6 and 10 distinct, practical recipes. " +
                                                        "For each recipe, provide a HIGH-QUALITY, valid Unsplash image URL in the 'image' field specifically for that dish (e.g., https://images.unsplash.com/photo-[ID]?auto=format&fit=crop&w=800&q=80). " +
                                                        "Ensure the image is appetizing and directly related to the recipe name. " +
                                                        "IMPORTANT: 'cookTime' and 'kcal' MUST be integers. Every ingredient MUST have a non-empty 'quantity' (e.g., '1 unit' if unknown). " +
                                                        "Steps must be HIGHLY DETAILED, actionable, and beginner-friendly (e.g., 'Heat 1 tbsp of olive oil in a pan over medium heat'). " +
                                                        "Include time and heat level for each step. Each recipe MUST include a 'tips' field with helpful advice. " +
                                                        "Return ONLY valid JSON in this format: " +
                                                        "{\"recipes\": [%s]}",
                                        allowedNames,
                                        String.join(", ", user.getAllergies()),
                                        String.join(", ", user.getDietaryPreferences()),
                                        RECIPE_JSON_FORMAT);

                        System.out.println("SCAN_LOG: Generating 6-10 recipes based on " + allowedIngredients.size() + " ingredients...");
                        String recipesJson = callOpenAi("Generate 6-10 recipes", generationPrompt);
                        System.out.println("SCAN_LOG: Recipe generation complete.");
                        JsonNode recipesNode = objectMapper.readTree(recipesJson);
                        List<CreateRecipeRequest> recipes = objectMapper.convertValue(
                                        recipesNode.path("recipes"), new TypeReference<List<CreateRecipeRequest>>() {
                                        });

                        return ScanResponse.builder()
                                        .allowed_ingredients(allowedIngredients)
                                        .restricted_ingredients(restrictedIngredients.stream()
                                                        .map(ri -> new AiIngredientDetectionResponse.RestrictedIngredient(
                                                                        ri.getName(),
                                                                        ri.getIcon() != null ? ri.getIcon() : "Restricted"))
                                                        .toList())
                                        .image_url(imageUrl)
                                        .recipes(recipes)
                                        .build();

                } catch (Exception e) {
                        System.err.println("SCAN_LOG ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        e.printStackTrace();
                        throw new BadRequestException("Failed to process scan: " + e.getMessage());
                }
        }

        @Override
        public ScanResponse scanTyped(List<String> ingredients, String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new BadRequestException("User not found"));

                try {
                        // 1. Categorize Typed Ingredients using GPT-4o-mini
                        String detectionPrompt = String.format(
                                        "Take this list of food ingredients: %s. " +
                                                        "Dietary restrictions: Allergies: %s, Preferences: %s. " +
                                                        "Categorize them into 'allowed_ingredients' (safe) and 'restricted_ingredients' (unsafe). "
                                                        +
                                                        "Assign a suitable food emoji icon to each. " +
                                                        "Return ONLY valid JSON in this format: " +
                                                        "{\"allowed_ingredients\": [{\"name\": \"ingredient name\", \"icon\": \"🥕\"}], \"restricted_ingredients\": [{\"name\": \"ingredient name\", \"icon\": \"🚫\", \"reason\": \"why\"}]}",
                                        ingredients.toString(),
                                        String.join(", ", user.getAllergies()),
                                        String.join(", ", user.getDietaryPreferences()));

                        System.out.println("SCAN_TYPED_LOG: Calling OpenAI for ingredient categorization...");
                        String detectionJson = callOpenAi("Categorize ingredients", detectionPrompt);
                        JsonNode detectionNode = objectMapper.readTree(detectionJson);

                        List<IngredientPayload> allowedIngredients = objectMapper.convertValue(
                                        detectionNode.path("allowed_ingredients"),
                                        new TypeReference<List<IngredientPayload>>() {
                                        });
                        List<IngredientPayload> restrictedIngredients = objectMapper.convertValue(
                                        detectionNode.path("restricted_ingredients"),
                                        new TypeReference<List<IngredientPayload>>() {
                                        });

                        if (allowedIngredients == null) allowedIngredients = new java.util.ArrayList<>();
                        if (restrictedIngredients == null) restrictedIngredients = new java.util.ArrayList<>();

                        // 2. Generate between 6 and 10 Recipes using GPT-4o-mini
                        String allowedNames = allowedIngredients.stream().map(IngredientPayload::getName).toList()
                                        .toString();
                        String generationPrompt = String.format(
                                        "Based on these ingredients: %s. " +
                                                        "User Allergies: %s, Preferences: %s. " +
                                                        "Generate between 6 and 10 distinct, practical recipes. " +
                                                        "For each recipe, provide a HIGH-QUALITY, valid Unsplash image URL in the 'image' field specifically for that dish (e.g., https://images.unsplash.com/photo-[ID]?auto=format&fit=crop&w=800&q=80). " +
                                                        "IMPORTANT: 'cookTime' and 'kcal' MUST be integers. Every ingredient MUST have a non-empty 'quantity' (e.g., '1 unit' if unknown). " +
                                                        "Steps must be HIGHLY DETAILED, actionable, and beginner-friendly (e.g., 'Heat 1 tbsp of olive oil in a pan over medium heat'). " +
                                                        "Include time and heat level for each step. Each recipe MUST include a 'tips' field with helpful advice. " +
                                                        "Return ONLY valid JSON in this format: " +
                                                        "{\"recipes\": [%s]}",
                                        allowedNames,
                                        String.join(", ", user.getAllergies()),
                                        String.join(", ", user.getDietaryPreferences()),
                                        RECIPE_JSON_FORMAT);

                        System.out.println("SCAN_TYPED_LOG: Generating 6-10 recipes...");
                        String recipesJson = callOpenAi("Generate 6-10 recipes", generationPrompt);
                        JsonNode recipesNode = objectMapper.readTree(recipesJson);
                        List<CreateRecipeRequest> recipes = objectMapper.convertValue(
                                        recipesNode.path("recipes"), new TypeReference<List<CreateRecipeRequest>>() {
                                        });

                        return ScanResponse.builder()
                                        .allowed_ingredients(allowedIngredients)
                                        .restricted_ingredients(restrictedIngredients.stream()
                                                        .map(ri -> new AiIngredientDetectionResponse.RestrictedIngredient(
                                                                        ri.getName(),
                                                                        ri.getIcon() != null ? ri.getIcon() : "Restricted"))
                                                        .toList())
                                        .image_url(null) // No image for typed scan
                                        .recipes(recipes)
                                        .build();

                } catch (Exception e) {
                        System.err.println("SCAN_TYPED_LOG ERROR: " + e.getMessage());
                        throw new BadRequestException("Failed to process typed scan: " + e.getMessage());
                }
        }

        private static final String RECIPE_JSON_FORMAT = """
                        {
                          "name": "Recipe Name",
                          "cookTime": 30,
                          "kcal": 500,
                          "image": "https://images.unsplash.com/photo-...",
                          "ingredients": [
                            {
                              "name": "ingredient name",
                              "quantity": "2 cups",
                              "icon": "🥕"
                            }
                          ],
                          "steps": [
                            "Step 1",
                            "Step 2"
                          ],
                          "tips": "Pro-tip/Note for this specific recipe"
                        }""";

        private String callOpenAiVision(String imageUrl, String prompt) throws JsonProcessingException {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(openAiApiKey);

                Map<String, Object> requestBody = Map.of(
                                "model", "gpt-4o",
                                "messages", List.of(
                                                Map.of("role", "user", "content", List.of(
                                                                Map.of("type", "text", "text", prompt),
                                                                Map.of("type", "image_url", "image_url",
                                                                                Map.of("url", imageUrl))))),
                                "max_tokens", 1000,
                                "temperature", 0.1);

                HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
                ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, request, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JsonNode root = objectMapper.readTree(response.getBody());
                        return root.path("choices").get(0).path("message").path("content").asText();
                }

                throw new BadRequestException("Failed to analyze image with AI.");
        }

        private String callOpenAi(String content, String systemPrompt) throws JsonProcessingException {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(openAiApiKey);

                Map<String, Object> requestBody = Map.of(
                                "model", "gpt-4o-mini",
                                "messages", List.of(
                                                Map.of("role", "system", "content", systemPrompt),
                                                Map.of("role", "user", "content", content)),
                                "response_format", Map.of("type", "json_object"),
                                "max_tokens", 4000,
                                "temperature", 0.0);

                HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
                ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, request, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        JsonNode root = objectMapper.readTree(response.getBody());
                        return root.path("choices").get(0).path("message").path("content").asText();
                }

                throw new BadRequestException("Failed to extract recipe from AI.");
        }

        @Override
        public List<Map<String, String>> searchWeb(String query) {
                try {
                        String searchUrl = "https://html.duckduckgo.com/html/?q="
                                        + java.net.URLEncoder.encode(query + " recipe", "UTF-8");
                        Document doc = Jsoup.connect(searchUrl)
                                        .userAgent("Mozilla/5.0")
                                        .get();

                        return doc.select(".result").stream()
                                        .limit(10)
                                        .map(element -> {
                                                String title = element.select(".result__title").text();
                                                String snippet = element.select(".result__snippet").text();
                                                String actualUrl = element.select(".result__a").attr("href");

                                                // Handle DDG proxy URLs: //duckduckgo.com/l/?u=URL... or
                                                // //duckduckgo.com/l/?uddg=URL...
                                                if (actualUrl.startsWith("//")) {
                                                        actualUrl = "https:" + actualUrl;
                                                }

                                                if (actualUrl.contains("?u=") || actualUrl.contains("?uddg=")) {
                                                        try {
                                                                String param = actualUrl.contains("?u=") ? "?u="
                                                                                : "?uddg=";
                                                                String encodedUrl = actualUrl
                                                                                .substring(actualUrl.indexOf(param)
                                                                                                + param.length());
                                                                String decoded = java.net.URLDecoder.decode(encodedUrl,
                                                                                java.nio.charset.StandardCharsets.UTF_8);

                                                                // Extract before & if any
                                                                if (decoded.contains("&")) {
                                                                        decoded = decoded.substring(0,
                                                                                        decoded.indexOf("&"));
                                                                }
                                                                actualUrl = decoded;
                                                        } catch (Exception e) {
                                                                // ignore
                                                        }
                                                }

                                                return Map.of(
                                                                "title", title,
                                                                "url", actualUrl,
                                                                "snippet", snippet);
                                        })
                                        .filter(m -> !m.get("url").isEmpty())
                                        .toList();
                } catch (IOException e) {
                        throw new BadRequestException("Web search failed: " + e.getMessage());
                }
        }
}
