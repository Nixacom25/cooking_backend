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
                Map<String, Object> recipe = (Map<String, Object>) ((Map<String, Object>) data.get("recipe")).get("recipe");
                recipe.put("origin", "IMPORT");
                recipe.put("sourceUrl", url);
                return objectMapper.convertValue(recipe, CreateRecipeRequest.class);
            }
        } catch (Exception e) {
            log.error("Extraction failed: {}", e.getMessage());
        }
        throw new BadRequestException("Échec de l'extraction");
    }

    @Override
    public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email) {
        try {
            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/generate", request, ScanResponse.class);
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
        throw new UnsupportedOperationException("Utilisez scanTyped.");
    }

    @Override
    public ScanResponse scanTyped(List<String> ingredients, String email) {
        Map<String, Object> body = Map.of("ingredients", ingredients);
        ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/generate", body, ScanResponse.class);
        ScanResponse res = response.getBody();
        if (res != null && res.getRecipes() != null) {
            for (CreateRecipeRequest r : res.getRecipes()) r.setOrigin("SCAN");
        }
        return res;
    }

    @Override
    public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
        throw new UnsupportedOperationException("Non supporté.");
    }
}
