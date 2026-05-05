package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.dto.response.ScanResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiService;
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

    private final com.cooked.backend.repository.RecipeDataRepository recipeDataRepository;

    @Value("${AI_API_BASE_URL:https://recipe.markhorsystems.com}")
    private String baseUrl;

    @Override
    public List<Map<String, String>> searchWeb(String query) {
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
        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<CreateRecipeRequest> generateInitialRecipes(User user, int count) {
        try {
            List<com.cooked.backend.entity.RecipeData> pool = recipeDataRepository.findRandomRecipes(20);
            List<String> poolNames = pool.stream().map(com.cooked.backend.entity.RecipeData::getName).toList();

            Map<String, Object> body = Map.of(
                "recipe_pool", poolNames,
                "user_preferences", Map.of(
                    "allergies", user.getAllergies() != null ? user.getAllergies() : "",
                    "preferences", user.getDietaryPreferences() != null ? user.getDietaryPreferences() : "",
                    "cuisines", user.getFavoriteCuisines() != null ? user.getFavoriteCuisines() : ""
                )
            );

            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/suggest", body, ScanResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<CreateRecipeRequest> recipes = response.getBody().getRecipes();
                for (CreateRecipeRequest r : recipes) {
                    pool.stream().filter(p -> p.getName().equalsIgnoreCase(r.getName()))
                        .findFirst().ifPresent(p -> r.setImage(p.getImageUrl()));
                    r.setOrigin("ONBOARDING");
                }
                return recipes;
            }
        } catch (Exception e) {
            log.error("Initial suggestions failed: {}", e.getMessage());
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
        return List.of("Tacos au poulet", "Pasta Carbonara", "Salade César", "Sushi Roll");
    }

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
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
        } catch (Exception e) {
            log.error("Extraction failed: {}", e.getMessage());
        }
        throw new BadRequestException("Échec de l'extraction");
    }

    @Override
    public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Utilisateur non trouvé"));

        try {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("allergies", user.getAllergies());
            prefs.put("preferences", user.getDietaryPreferences());
            prefs.put("cuisines_love", user.getFavoriteCuisines());
            prefs.put("system_instructions", "Soyez extrêmement précis et généreux dans la section 'tips' (notes et conseils) pour chaque recette. Incluez des conseils sur la texture, les variantes de saveurs, et la conservation.");

            Map<String, Object> body = new HashMap<>();
            body.put("ingredients", request.getIngredients());
            body.put("user_preferences", prefs);

            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/generate", body, ScanResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<CreateRecipeRequest> recipes = response.getBody().getRecipes();
                for (CreateRecipeRequest r : recipes) r.setOrigin("MANUAL");
                return recipes;
            }
        } catch (Exception e) {
            log.error("Generation failed: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public ScanResponse scan(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Utilisateur non trouvé"));

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", file.getResource());
            
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("allergies", user.getAllergies());
            prefs.put("preferences", user.getDietaryPreferences());
            prefs.put("cuisines_love", user.getFavoriteCuisines());
            prefs.put("kitchen_tools", user.getKitchenAppliances());
            prefs.put("skill_level", user.getCookingSkill());
            prefs.put("system_instructions", "Soyez extrêmement précis et généreux dans la section 'tips' (notes et conseils) pour chaque recette. Incluez des conseils sur la texture, les variantes de saveurs, et la conservation.");
            
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
        } catch (Exception e) {
            log.error("Scan failed: {}", e.getMessage());
        }
        throw new BadRequestException("Échec de l'analyse de l'image");
    }

    @Override
    public ScanResponse scanTyped(List<String> ingredients, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Utilisateur non trouvé"));

        try {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("allergies", user.getAllergies());
            prefs.put("preferences", user.getDietaryPreferences());
            prefs.put("cuisines_love", user.getFavoriteCuisines());
            prefs.put("system_instructions", "Soyez extrêmement précis et généreux dans la section 'tips' (notes et conseils) pour chaque recette. Incluez des conseils sur la texture, les variantes de saveurs, et la conservation.");

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
        } catch (Exception e) {
            log.error("ScanTyped failed: {}", e.getMessage());
        }
        throw new BadRequestException("Échec de la génération des recettes par ingrédients");
    }

    @Override
    public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Utilisateur non trouvé"));

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
        } catch (Exception e) {
            log.error("Detection failed: {}", e.getMessage());
        }
        throw new BadRequestException("Échec de la détection des ingrédients");
    }
}
