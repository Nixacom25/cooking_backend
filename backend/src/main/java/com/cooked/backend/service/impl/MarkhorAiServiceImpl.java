package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.dto.response.ScanResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiService;
import com.cooked.backend.service.SubscriptionService;
import com.cooked.backend.exception.PaymentRequiredException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Primary
@RequiredArgsConstructor
public class MarkhorAiServiceImpl implements AiService {
    private static final Logger log = LoggerFactory.getLogger(MarkhorAiServiceImpl.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final com.cooked.backend.repository.RecipeRepository recipeRepository;

    private final com.cooked.backend.repository.RecipeDataRepository recipeDataRepository;

    @Value("${AI_API_BASE_URL:https://recipe.markhorsystems.com}")
    private String baseUrl;

    @Override
    public List<Map<String, String>> searchWeb(String query, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);

        try {
            log.info("Native Search for: {}", query);
            String q = query.toLowerCase().contains("recipe") ? query : query + " recipe";
            String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            List<Map<String, String>> results = new ArrayList<>();
            Elements items = doc.select(".result__body");

            for (Element item : items) {
                if (results.size() >= 10) break;
                Element a = item.selectFirst(".result__title a");
                if (a != null) {
                    Map<String, String> res = new HashMap<>();
                    res.put("title", a.text());
                    res.put("url", a.attr("href").startsWith("//") ? "https:" + a.attr("href") : a.attr("href"));
                    Element snip = item.selectFirst(".result__snippet");
                    res.put("snippet", snip != null ? snip.text() : "");
                    results.add(res);
                }
            }
            return results;
        } catch (PaymentRequiredException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<CreateRecipeRequest> generateInitialRecipes(User user, int count) {
        try {
            Map<String, Object> body = Map.of(
                "user_preferences", Map.of(
                    "allergies", user.getAllergies() != null ? user.getAllergies() : "",
                    "preferences", user.getDietaryPreferences() != null ? user.getDietaryPreferences() : "",
                    "cuisines", user.getFavoriteCuisines() != null ? user.getFavoriteCuisines() : "",
                    "flavorDna", user.getFlavorDna() != null ? user.getFlavorDna() : "",
                    "skill", user.getCookingSkill() != null ? user.getCookingSkill() : ""
                )
            );

            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/api/recipes/suggest", body, String.class);
            log.info("AI Service suggest call for {}. Status: {}", user.getEmail(), response.getStatusCode());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode recipesNode = root.path("recipes");
                
                if (recipesNode.isArray()) {
                    List<CreateRecipeRequest> recipes = objectMapper.convertValue(
                        recipesNode, 
                        new com.fasterxml.jackson.core.type.TypeReference<List<CreateRecipeRequest>>() {}
                    );
                    for (CreateRecipeRequest r : recipes) {
                        r.setOrigin("ONBOARDING");
                    }
                    log.info("Found {} initial suggestions for {}", recipes.size(), user.getEmail());
                    return recipes;
                }
            }
        } catch (Exception e) {
            log.error("Initial suggestions AI call failed for user {}: {}. Falling back to curated EXPLORE recipes.", 
                user.getEmail(), e.getMessage());
            
            try {
                // Fallback: Fetch 4 random EXPLORE recipes matching user cuisines if possible
                List<String> preferredCuisines = user.getFavoriteCuisines() != null 
                    ? Arrays.asList(user.getFavoriteCuisines().split(",\\s*"))
                    : null;
                
                org.springframework.data.domain.Page<com.cooked.backend.entity.Recipe> exploreRecipes = 
                    recipeRepository.findRandomPopularRecipes(null, preferredCuisines, org.springframework.data.domain.PageRequest.of(0, 4));
                
                if (exploreRecipes.isEmpty() && preferredCuisines != null) {
                    // If no match for specific cuisines, get any popular recipes
                    exploreRecipes = recipeRepository.findRandomPopularRecipes(null, null, org.springframework.data.domain.PageRequest.of(0, 4));
                }

                List<CreateRecipeRequest> fallbackRecipes = new ArrayList<>();
                for (com.cooked.backend.entity.Recipe r : exploreRecipes.getContent()) {
                    CreateRecipeRequest req = new CreateRecipeRequest();
                    req.setName(r.getName());
                    req.setImage(r.getImage());
                    req.setCookTime(r.getCookTime());
                    req.setPrepTime(r.getPrepTime());
                    req.setKcal(r.getKcal());
                    req.setServings(r.getServings());
                    req.setCuisine(r.getCuisine());
                    req.setCategory(r.getCategory());
                    req.setTips(r.getTips());
                    req.setOrigin("ONBOARDING");
                    req.setSourceUrl(r.getSourceUrl());
                    
                    List<com.cooked.backend.dto.request.IngredientPayload> ingredients = new ArrayList<>();
                    if (r.getRecipeIngredients() != null) {
                        for (com.cooked.backend.entity.RecipeIngredient ri : r.getRecipeIngredients()) {
                            com.cooked.backend.dto.request.IngredientPayload ip = new com.cooked.backend.dto.request.IngredientPayload();
                            ip.setName(ri.getIngredient().getName());
                            ip.setQuantity(ri.getQuantity());
                            ip.setIcon(ri.getIngredient().getIcon());
                            ingredients.add(ip);
                        }
                    }
                    req.setIngredients(ingredients);
                    req.setSteps(r.getSteps());
                    req.setEquipment(r.getEquipment());
                    fallbackRecipes.add(req);
                }
                
                log.info("Returning {} fallback EXPLORE recipes for user {}", fallbackRecipes.size(), user.getEmail());
                return fallbackRecipes;
            } catch (Exception fallbackEx) {
                log.error("Emergency fallback also failed for {}: {}", user.getEmail(), fallbackEx.getMessage());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> generateTrendingDishes() {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(baseUrl + "/api/recipes/trending", HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (List<String>) response.getBody().get("trending");
            }
        } catch (Exception e) {
            log.warn("Trending call failed, using defaults");
        }
        return List.of("Chicken Tacos", "Pasta Carbonara", "Caesar Salad", "Sushi Roll");
    }

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);
        try {
            Map<String, String> body = Map.of("url", url);
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(baseUrl + "/api/extract", body, (Class<Map<String, Object>>)(Class<?>)Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                Map<String, Object> recipeMap = (Map<String, Object>) ((Map<String, Object>) data.get("recipe")).get("recipe");
                recipeMap.put("origin", "IMPORT");
                recipeMap.put("sourceUrl", url);
                
                CreateRecipeRequest request = objectMapper.convertValue(recipeMap, CreateRecipeRequest.class);
                
                // Fallback image extraction if the API missed it
                if (request.getImage() == null || request.getImage().trim().isEmpty() || request.getImage().contains("placeholder")) {
                    log.info("API missed image for {}, attempting fallback scraping", url);
                    try {
                        Document doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                .timeout(5000)
                                .get();
                        
                        String ogImage = doc.select("meta[property=og:image]").attr("content");
                        if (!ogImage.isEmpty()) {
                            request.setImage(ogImage);
                        } else {
                            String twitterImage = doc.select("meta[name=twitter:image]").attr("content");
                            if (!twitterImage.isEmpty()) {
                                request.setImage(twitterImage);
                            } else {
                                // Try first large image in the article
                                Element firstImage = doc.select("article img, .recipe-image img, .entry-content img").first();
                                if (firstImage != null) {
                                    String src = firstImage.absUrl("src");
                                    if (!src.isEmpty()) request.setImage(src);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Fallback scraping failed for image: {}", e.getMessage());
                    }
                }
                
                return request;
            }
            throw new BadRequestException("Unable to extract recipe from this link");
        } catch (BadRequestException | PaymentRequiredException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Extraction failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new BadRequestException("The AI extraction service is currently busy. Please try again in a few seconds or scan a screenshot instead.");
            }
            throw new BadRequestException("We couldn't extract the recipe from this link. Please try another one.");
        } catch (Exception e) {
            log.error("Extraction failed: {}", e.getMessage());
            throw new BadRequestException("We couldn't extract the recipe from this link. Please try another one.");
        }
    }

    @Override
    public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        verifyAiAccess(user);

        try {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("allergies", user.getAllergies());
            prefs.put("preferences", user.getDietaryPreferences());
            prefs.put("cuisines_love", user.getFavoriteCuisines());
            prefs.put("kitchen_tools", user.getKitchenAppliances());
            prefs.put("skill_level", normalizeSkillLevel(user.getCookingSkill()));
            prefs.put("system_instructions", "Be extremely precise and generous in the 'tips' (notes and advice) section for each recipe. Include advice on texture, flavor variations, and storage.");

            Map<String, Object> body = new HashMap<>();
            body.put("ingredients", request.getIngredients());
            body.put("user_preferences", prefs);

            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/generate", body, ScanResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<CreateRecipeRequest> recipes = response.getBody().getRecipes();
                for (CreateRecipeRequest r : recipes) r.setOrigin("MANUAL");
                return recipes;
            }
        } catch (PaymentRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("Generation failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public ScanResponse scan(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        verifyAiAccess(user);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", file.getResource());
            
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("allergies", user.getAllergies());
            prefs.put("preferences", user.getDietaryPreferences());
            prefs.put("cuisines_love", user.getFavoriteCuisines());
            prefs.put("kitchen_tools", user.getKitchenAppliances());
            prefs.put("skill_level", normalizeSkillLevel(user.getCookingSkill()));
            prefs.put("system_instructions", "Be extremely precise and generous in the 'tips' (notes and advice) section for each recipe. Include advice on texture, flavor variations, and storage.");
            
            body.add("user_preferences", objectMapper.writeValueAsString(prefs));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/analyze", requestEntity, ScanResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ScanResponse res = response.getBody();
                if (res.getRecipes() != null) {
                    for (CreateRecipeRequest r : res.getRecipes()) {
                        r.setOrigin("SCAN");
                    }
                }
                return res;
            }
            throw new BadRequestException("Image analysis failed: Invalid response from AI service");
        } catch (BadRequestException | PaymentRequiredException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Scan failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 422) {
                throw new BadRequestException("We couldn't detect any ingredients in your photo. Try taking a clearer picture with better lighting.");
            }
            throw new BadRequestException("The recipe scan service is currently busy or unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Scan failed: {}", e.getMessage());
            throw new BadRequestException("The recipe scan service is currently unavailable. Please try again in a few moments.");
        }
    }

    @Override
    public ScanResponse scanTyped(List<String> ingredients, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        verifyAiAccess(user);

        try {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("allergies", user.getAllergies());
            prefs.put("preferences", user.getDietaryPreferences());
            prefs.put("cuisines_love", user.getFavoriteCuisines());
            prefs.put("kitchen_tools", user.getKitchenAppliances());
            prefs.put("skill_level", normalizeSkillLevel(user.getCookingSkill()));
            prefs.put("system_instructions", "Be extremely precise and generous in the 'tips' (notes and advice) section for each recipe. Include advice on texture, flavor variations, and storage.");

            Map<String, Object> body = new HashMap<>();
            body.put("ingredients", ingredients);
            body.put("user_preferences", prefs);

            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/generate", body, ScanResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ScanResponse res = response.getBody();
                
                // If the microservice didn't categorize, we can at least mark the recipes
                if (res.getRecipes() != null) {
                    for (CreateRecipeRequest r : res.getRecipes()) {
                        r.setOrigin("SCAN");
                    }
                }
                
                // Ensure allowed_ingredients is populated if it was just a list of strings
                if (res.getAllowed_ingredients() == null || res.getAllowed_ingredients().isEmpty()) {
                    List<com.cooked.backend.dto.request.IngredientPayload> allowed = new ArrayList<>();
                    for (String ing : ingredients) {
                        com.cooked.backend.dto.request.IngredientPayload p = new com.cooked.backend.dto.request.IngredientPayload();
                        p.setName(ing);
                        p.setQuantity("-");
                        allowed.add(p);
                    }
                    res.setAllowed_ingredients(allowed);
                }
                
                return res;
            }
            throw new BadRequestException("Failed to generate recipes: Invalid response from AI service");
        } catch (BadRequestException | PaymentRequiredException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("ScanTyped failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new BadRequestException("Too many requests. Please wait a few moments before trying again.");
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new BadRequestException("The AI service is temporarily unavailable. Please try again later.");
            }
            throw new BadRequestException("We encountered an issue while generating your recipe. Please try again.");
        } catch (Exception e) {
            log.error("ScanTyped failed: {}", e.getMessage(), e);
            throw new BadRequestException("We encountered an issue while generating your recipe. Please try again.");
        }
    }

    @Override
    public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        verifyAiAccess(user);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", file.getResource());
            
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("allergies", user.getAllergies());
            prefs.put("preferences", user.getDietaryPreferences());
            
            body.add("user_preferences", objectMapper.writeValueAsString(prefs));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<AiIngredientDetectionResponse> response = restTemplate.postForEntity(
                baseUrl + "/api/ingredients/detect", 
                requestEntity, 
                AiIngredientDetectionResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new BadRequestException("Ingredient detection failed: Invalid response from AI service");
        } catch (BadRequestException | PaymentRequiredException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Detection failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 422) {
                throw new BadRequestException("We couldn't identify ingredients in this photo. Please try a clearer shot.");
            }
            throw new BadRequestException("Ingredient detection is currently unavailable. Please try again.");
        } catch (Exception e) {
            log.error("Detection failed: {}", e.getMessage());
            throw new BadRequestException("We encountered an issue while detecting your ingredients. Please try again.");
        }
    }

    private String normalizeSkillLevel(String skill) {
        if (skill == null) return "HomeCook";
        String s = skill.trim();
        if (s.equalsIgnoreCase("Total Beginner")) return "total_beginer";
        if (s.equalsIgnoreCase("Home Cook")) return "HomeCook";
        if (s.equalsIgnoreCase("Confident Cook")) return "ConfidentCook";
        if (s.equalsIgnoreCase("Advanced / Semi-Pro")) return "Advanced Semi Pro";
        return "HomeCook"; // Fallback
    }

    private void verifyAiAccess(User user) {
        if (!subscriptionService.hasAiAccess(user)) {
            throw new PaymentRequiredException("AI access requires a premium subscription or trial.");
        }
    }
}
