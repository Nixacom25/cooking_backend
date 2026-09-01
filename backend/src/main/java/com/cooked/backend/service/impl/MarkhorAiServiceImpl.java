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

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "with", "and", "or", "in", "on", "at", "the", "a", "an", "of", "for", "to",
        "de", "la", "le", "les", "des", "au", "aux", "un", "une", "et", "ou",
        "recipe", "recette", "style", "dish", "plat", "sauce", "easy", "quick",
        "facile", "rapide", "healthy", "sain"
    ));

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final com.cooked.backend.repository.RecipeRepository recipeRepository;
    private final com.cooked.backend.repository.IngredientRepository ingredientRepository;

    @Value("${ai.api.base-url:https://recipe.markhorsystems.com}")
    private String baseUrl;

    @Value("${ai.internal.secret:cooked_internal_bypass_secret_2024}")
    private String internalSecret;

    private HttpHeaders getInternalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Override
    public List<Map<String, String>> searchWeb(String query, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        verifyAiAccess(user);

        // Sequential multi-engine search for maximum reliability on Render
        try {
            return performGoogleSearch(query);
        } catch (Exception e1) {
            log.warn("Google search failed, trying DuckDuckGo: {}", e1.getMessage());
            try {
                return performDuckDuckGoSearch(query);
            } catch (Exception e2) {
                log.warn("DuckDuckGo search failed, trying Qwant Lite: {}", e2.getMessage());
                try {
                    return performQwantLiteSearch(query);
                } catch (Exception e3) {
                    log.warn("Qwant search failed, trying Mojeek: {}", e3.getMessage());
                    try {
                        return performMojeekSearch(query);
                    } catch (Exception e4) {
                        log.warn("Mojeek search failed, trying Bing: {}", e4.getMessage());
                        try {
                            return performBingSearch(query);
                        } catch (Exception e5) {
                            log.error("All search engines failed on Render: {}", e5.getMessage());
                            return Collections.emptyList();
                        }
                    }
                }
            }
        }
    }

    private Map<String, String> getHumanHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.put("Accept-Language", "en-US,en;q=0.9,fr;q=0.8");
        headers.put("Cache-Control", "max-age=0");
        headers.put("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"");
        headers.put("Sec-Ch-Ua-Mobile", "?0");
        headers.put("Sec-Ch-Ua-Platform", "\"Windows\"");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Upgrade-Insecure-Requests", "1");
        return headers;
    }

    private List<Map<String, String>> performGoogleSearch(String query) throws Exception {
        log.info("Google Search for: {}", query);
        String q = query.toLowerCase().contains("recipe") ? query : query + " recipe";
        String url = "https://www.google.com/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&gbv=1&hl=en";

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .headers(getHumanHeaders())
                    .cookie("SOCS", "CAESHAgBEhJnd3NfMjAyNDA1MDgtMF9SQzEaAmVuIAEaBgiA_LmwBg")
                    .timeout(10000)
                    .get();

            if (doc.title().contains("Before you continue") || !doc.select("form[action*='consent']").isEmpty()) {
                log.warn("Google consent page detected, attempting bypass...");
                Element form = doc.select("form").first();
                if (form != null) {
                    String action = form.absUrl("action");
                    Map<String, String> data = new HashMap<>();
                    for (Element input : form.select("input[type=hidden]")) {
                        data.put(input.attr("name"), input.attr("value"));
                    }
                    data.put("set_eom", "true"); 
                    doc = Jsoup.connect(action).data(data).method(org.jsoup.Connection.Method.POST)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                            .headers(getHumanHeaders()).timeout(10000).execute().parse();
                }
            }

            List<Map<String, String>> results = new ArrayList<>();
            Elements items = doc.select("div.ZINbbc");
            for (Element item : items) {
                if (results.size() >= 10) break;
                Element a = item.selectFirst("a:has(h3)");
                if (a == null) a = item.selectFirst("a");
                Element h3 = item.selectFirst("h3");
                if (h3 != null && a != null) {
                    String rawUrl = a.attr("href");
                    String cleanUrl = rawUrl;
                    if (rawUrl.startsWith("/url?q=")) {
                        try {
                            cleanUrl = java.net.URLDecoder.decode(rawUrl.split("url\\?q=")[1].split("&")[0], StandardCharsets.UTF_8);
                        } catch (Exception e) { continue; }
                    }
                    if (cleanUrl.startsWith("http") && !cleanUrl.contains("google.com/")) {
                        Map<String, String> res = new HashMap<>();
                        res.put("title", h3.text());
                        res.put("url", cleanUrl);
                        Element snippet = item.select("div.BNeawe").last();
                        res.put("snippet", snippet != null ? snippet.text() : "");
                        results.add(res);
                    }
                }
            }
            if (results.isEmpty()) throw new Exception("Google returned no results. Title: " + doc.title());
            return results;
        } catch (org.jsoup.HttpStatusException e) {
            log.error("Google search HTTP error: Status={}, URL={}", e.getStatusCode(), e.getUrl());
            throw e;
        } catch (Exception e) {
            log.error("Google search failed: {}", e.getMessage());
            throw e;
        }
    }

    private List<Map<String, String>> performDuckDuckGoSearch(String query) throws Exception {
        log.info("DDG Search for: {}", query);
        String q = query.toLowerCase().contains("recipe") ? query : query + " recipe";
        String url = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .headers(getHumanHeaders())
                    .timeout(8000)
                    .get();

            List<Map<String, String>> results = new ArrayList<>();
            Elements rows = doc.select("tr");
            for (int i = 0; i < rows.size(); i++) {
                if (results.size() >= 10) break;
                Element row = rows.get(i);
                
                Element a = row.selectFirst("a.result-link");
                if (a == null) a = row.selectFirst("a[href^='http'], a[href^='//']");
                
                if (a != null) {
                    Map<String, String> res = new HashMap<>();
                    res.put("title", a.text());
                    String href = a.attr("href");
                    
                    if (href.contains("uddg=")) {
                        try {
                            res.put("url", java.net.URLDecoder.decode(href.split("uddg=")[1].split("&")[0], StandardCharsets.UTF_8));
                        } catch (Exception e) { res.put("url", href); }
                    } else {
                        res.put("url", href.startsWith("//") ? "https:" + href : href);
                    }
                    
                    if (i + 1 < rows.size()) {
                        Element snippet = rows.get(i + 1).selectFirst(".result-snippet");
                        if (snippet == null) snippet = rows.get(i + 1).selectFirst("td");
                        res.put("snippet", snippet != null ? snippet.text() : "");
                    } else {
                        res.put("snippet", "");
                    }
                    
                    if (!res.get("url").contains("duckduckgo.com/")) {
                        results.add(res);
                    }
                }
            }
            if (results.isEmpty()) throw new Exception("DDG returned no results. Title: " + doc.title());
            return results;
        } catch (org.jsoup.HttpStatusException e) {
            log.error("DuckDuckGo HTTP error: Status={}, URL={}", e.getStatusCode(), e.getUrl());
            throw e;
        } catch (Exception e) {
            log.error("DuckDuckGo search failed: {}", e.getMessage());
            throw e;
        }
    }

    private List<Map<String, String>> performQwantLiteSearch(String query) throws Exception {
        log.info("Qwant Search for: {}", query);
        String q = query.toLowerCase().contains("recipe") ? query : query + " recipe";
        String url = "https://lite.qwant.com/?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .headers(getHumanHeaders())
                .timeout(8000)
                .get();

        List<Map<String, String>> results = new ArrayList<>();
        Elements items = doc.select("section article");
        for (Element item : items) {
            if (results.size() >= 10) break;
            Element a = item.selectFirst("h2 a");
            if (a != null) {
                Map<String, String> res = new HashMap<>();
                res.put("title", a.text());
                res.put("url", a.attr("href"));
                Element snippet = item.selectFirst("p");
                res.put("snippet", snippet != null ? snippet.text() : "");
                results.add(res);
            }
        }
        if (results.isEmpty()) throw new Exception("Qwant returned no results");
        return results;
    }

    private List<Map<String, String>> performMojeekSearch(String query) throws Exception {
        log.info("Mojeek Search for: {}", query);
        String q = query.toLowerCase().contains("recipe") ? query : query + " recipe";
        String url = "https://www.mojeek.com/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .headers(getHumanHeaders())
                .timeout(8000)
                .get();

        List<Map<String, String>> results = new ArrayList<>();
        Elements items = doc.select("ul.results-standard > li");
        for (Element item : items) {
            if (results.size() >= 10) break;
            Element a = item.selectFirst("h2 a, a.ob");
            if (a != null) {
                Map<String, String> res = new HashMap<>();
                res.put("title", a.text());
                res.put("url", a.attr("href"));
                Element snippet = item.selectFirst("p.s");
                res.put("snippet", snippet != null ? snippet.text() : "");
                results.add(res);
            }
        }
        if (results.isEmpty()) throw new Exception("Mojeek returned no results");
        return results;
    }

    private List<Map<String, String>> performBingSearch(String query) throws Exception {
        log.info("Bing Search for: {}", query);
        String q = query.toLowerCase().contains("recipe") ? query : query + " recipe";
        String url = "https://www.bing.com/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .headers(getHumanHeaders())
                .timeout(8000)
                .get();

        List<Map<String, String>> results = new ArrayList<>();
        Elements items = doc.select("li.b_algo");
        for (Element item : items) {
            if (results.size() >= 10) break;
            Element a = item.selectFirst("h2 a");
            if (a != null) {
                Map<String, String> res = new HashMap<>();
                res.put("title", a.text());
                res.put("url", a.attr("href"));
                Element snippet = item.selectFirst(".b_caption p, .b_lineclamp");
                res.put("snippet", snippet != null ? snippet.text() : "");
                results.add(res);
            }
        }
        if (results.isEmpty()) throw new Exception("Bing returned no results");
        return results;
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
                    "skill", user.getCookingSkill() != null ? user.getCookingSkill() : "",
                    "budget", user.getGroceryBudget() != null ? user.getGroceryBudget() : "",
                    "goals", user.getOnboardingGoals() != null ? user.getOnboardingGoals() : "",
                    "groceryFrequency", user.getGroceryFrequency() != null ? user.getGroceryFrequency() : "",
                    "groceryStores", user.getGroceryStores() != null ? user.getGroceryStores() : "",
                    "excitedFeatures", user.getExcitedFeatures() != null ? user.getExcitedFeatures() : ""
                )
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, getInternalHeaders());
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/api/recipes/suggest", requestEntity, String.class);
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
                    assignBestMatchingImages(recipes);
                    log.info("Found {} initial suggestions for {}", recipes.size(), user.getEmail());
                    return recipes;
                }
            }
        } catch (Exception e) {
            log.error("Initial suggestions AI call failed for user {}: {}. Falling back to curated EXPLORE recipes.", 
                user.getEmail(), e.getMessage());
            
            try {
                // Fallback: Fetch 4 random EXPLORE recipes matching user cuisines if possible
                List<String> preferredCuisines = (user.getFavoriteCuisines() != null && !user.getFavoriteCuisines().isEmpty()) 
                    ? user.getFavoriteCuisines() 
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
                    req.setCuisine(r.getCuisine() != null ? r.getCuisine().getName() : null);
                    req.setCategories(r.getCategories() != null ? r.getCategories().stream().map(c -> c.getName()).collect(java.util.stream.Collectors.toList()) : null);
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
            HttpEntity<Void> requestEntity = new HttpEntity<>(getInternalHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(baseUrl + "/api/recipes/trending", HttpMethod.GET, requestEntity, new ParameterizedTypeReference<Map<String, Object>>() {});
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
        
        boolean hasAiAccess = false;
        try {
            verifyAiAccess(user);
            hasAiAccess = true;
        } catch (PaymentRequiredException e) {
            log.info("User {} does not have AI access, falling back to direct Jsoup link extraction.", email);
        }

        if (url == null || url.trim().isEmpty()) {
            throw new BadRequestException("Please provide a valid URL.");
        }
        
        String cleanUrl = url.trim();
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://" + cleanUrl;
        }

        // 1. Try AI microservice extraction if AI access is enabled
        if (hasAiAccess) {
            try {
                Map<String, String> body = Map.of("url", cleanUrl);
                HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, getInternalHeaders());
                ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(baseUrl + "/api/extract", requestEntity, (Class<Map<String, Object>>)(Class<?>)Map.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> respBody = response.getBody();
                    Map<String, Object> recipeMap = null;

                    if (respBody.get("data") instanceof Map) {
                        Map<String, Object> data = (Map<String, Object>) respBody.get("data");
                        if (data.get("recipe") instanceof Map) {
                            Map<String, Object> rec = (Map<String, Object>) data.get("recipe");
                            if (rec.get("recipe") instanceof Map) {
                                recipeMap = (Map<String, Object>) rec.get("recipe");
                            } else {
                                recipeMap = rec;
                            }
                        } else {
                            recipeMap = data;
                        }
                    } else if (respBody.get("recipe") instanceof Map) {
                        Map<String, Object> rec = (Map<String, Object>) respBody.get("recipe");
                        if (rec.get("recipe") instanceof Map) {
                            recipeMap = (Map<String, Object>) rec.get("recipe");
                        } else {
                            recipeMap = rec;
                        }
                    } else {
                        recipeMap = respBody;
                    }

                    if (recipeMap != null && recipeMap.get("name") != null && !recipeMap.get("name").toString().trim().isEmpty()) {
                        recipeMap.put("origin", "IMPORT");
                        recipeMap.put("sourceUrl", cleanUrl);
                        
                        CreateRecipeRequest request = objectMapper.convertValue(recipeMap, CreateRecipeRequest.class);
                        
                        // Fallback image extraction if missing
                        if (request.getImage() == null || request.getImage().trim().isEmpty() || request.getImage().contains("placeholder")) {
                            log.info("API missed image for {}, attempting fallback scraping", cleanUrl);
                            try {
                                Document doc = Jsoup.connect(cleanUrl)
                                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                        .headers(getHumanHeaders())
                                        .followRedirects(true)
                                        .timeout(5000)
                                        .get();
                                
                                String ogImage = doc.select("meta[property=og:image]").attr("content");
                                if (!ogImage.isEmpty()) {
                                    request.setImage(ogImage);
                                } else {
                                    String twitterImage = doc.select("meta[name=twitter:image]").attr("content");
                                    if (!twitterImage.isEmpty()) {
                                        request.setImage(twitterImage);
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Fallback scraping failed for image: {}", e.getMessage());
                            }
                        }
                        
                        if (request.getIngredients() == null || request.getIngredients().isEmpty()) {
                            request.setIngredients(List.of(new com.cooked.backend.dto.request.IngredientPayload("Main Ingredients", "As listed in source", "🍳")));
                        }
                        if (request.getSteps() == null || request.getSteps().isEmpty()) {
                            request.setSteps(List.of("Follow instructions from the original recipe link."));
                        }
                        
                        return request;
                    }
                }
            } catch (Exception e) {
                log.warn("AI extraction microservice call failed for {}: {}. Trying Jsoup fallback...", cleanUrl, e.getMessage());
            }
        }

        // 2. Fallback to Jsoup HTML/JSON-LD Scraping if AI microservice failed or user doesn't have AI access
        try {
            return extractRecipeViaJsoup(cleanUrl);
        } catch (Exception e) {
            log.error("Jsoup fallback extraction failed for {}: {}", cleanUrl, e.getMessage());
            return createGenericFallbackRecipe(cleanUrl);
        }
    }

    private CreateRecipeRequest createGenericFallbackRecipe(String url) {
        String domain = url;
        try {
            java.net.URI uri = new java.net.URI(url);
            domain = uri.getHost();
            if (domain != null && domain.startsWith("www.")) {
                domain = domain.substring(4);
            }
        } catch (Exception ignored) {}

        String title = "Imported Recipe (" + (domain != null ? domain : "Web") + ")";
        
        CreateRecipeRequest req = new CreateRecipeRequest();
        req.setName(title);
        req.setImage("https://images.unsplash.com/photo-1495521821757-a1efb6729352?auto=format&fit=crop&w=800&q=80");
        req.setCookTime(20);
        req.setPrepTime(10);
        req.setKcal(400);
        req.setServings(2);
        req.setSourceUrl(url);
        req.setOrigin("IMPORT");
        
        com.cooked.backend.dto.request.IngredientPayload ip = new com.cooked.backend.dto.request.IngredientPayload();
        ip.setName("Recipe Ingredients");
        ip.setQuantity("See source website");
        ip.setIcon("🍳");
        req.setIngredients(List.of(ip));
        req.setSteps(List.of("Follow instructions from the original recipe link: " + url));
        req.setEquipment(new ArrayList<>());
        return req;
    }

    private CreateRecipeRequest extractRecipeViaJsoup(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .headers(getHumanHeaders())
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .timeout(10000)
                .get();

        String title = doc.select("meta[property=og:title]").attr("content");
        if (title.isEmpty()) title = doc.select("meta[name=twitter:title]").attr("content");
        if (title.isEmpty()) title = doc.title();
        if (title.isEmpty()) {
            title = "Imported Recipe";
        } else {
            title = title.replaceAll("(?i)\\s*[-|•]\\s*(marmiton|allrecipes|tasty|bbc good food|750g|cuisineAZ|youtube|tiktok|instagram).*", "").trim();
        }

        String image = doc.select("meta[property=og:image]").attr("content");
        if (image.isEmpty()) image = doc.select("meta[name=twitter:image]").attr("content");

        List<com.cooked.backend.dto.request.IngredientPayload> ingredients = new ArrayList<>();
        List<String> steps = new ArrayList<>();

        Elements scripts = doc.select("script[type=application/ld+json]");
        for (Element script : scripts) {
            try {
                JsonNode node = objectMapper.readTree(script.html());
                JsonNode recipeNode = findRecipeNodeInJsonLd(node);
                if (recipeNode != null) {
                    if (recipeNode.has("name") && !recipeNode.get("name").asText().isEmpty()) {
                        title = recipeNode.get("name").asText();
                    }
                    if (recipeNode.has("image")) {
                        JsonNode imgNode = recipeNode.get("image");
                        if (imgNode.isTextual()) image = imgNode.asText();
                        else if (imgNode.isArray() && imgNode.size() > 0) image = imgNode.get(0).asText();
                        else if (imgNode.has("url")) image = imgNode.get("url").asText();
                    }
                    if (recipeNode.has("recipeIngredient") && recipeNode.get("recipeIngredient").isArray()) {
                        for (JsonNode ing : recipeNode.get("recipeIngredient")) {
                            com.cooked.backend.dto.request.IngredientPayload ip = new com.cooked.backend.dto.request.IngredientPayload();
                            ip.setName(ing.asText());
                            ip.setQuantity("1 unit");
                            ip.setIcon("🍳");
                            ingredients.add(ip);
                        }
                    }
                    if (recipeNode.has("recipeInstructions") && recipeNode.get("recipeInstructions").isArray()) {
                        for (JsonNode step : recipeNode.get("recipeInstructions")) {
                            if (step.isTextual()) steps.add(step.asText());
                            else if (step.has("text")) steps.add(step.get("text").asText());
                        }
                    }
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (ingredients.isEmpty()) {
            Elements ingElems = doc.select(".recipe-ingredients li, .ingredients li, [class*='ingredient'] li, [itemprop=recipeIngredient]");
            for (Element el : ingElems) {
                String text = el.text().trim();
                if (!text.isEmpty() && text.length() < 100) {
                    com.cooked.backend.dto.request.IngredientPayload ip = new com.cooked.backend.dto.request.IngredientPayload();
                    ip.setName(text);
                    ip.setQuantity("1 unit");
                    ip.setIcon("🍳");
                    ingredients.add(ip);
                }
            }
        }

        if (steps.isEmpty()) {
            Elements stepElems = doc.select(".recipe-instructions li, .instructions li, [class*='instruction'] li, [class*='step'] li, [itemprop=recipeInstructions]");
            for (Element el : stepElems) {
                String text = el.text().trim();
                if (!text.isEmpty() && text.length() > 5) {
                    steps.add(text);
                }
            }
        }

        if (ingredients.isEmpty()) {
            com.cooked.backend.dto.request.IngredientPayload ip = new com.cooked.backend.dto.request.IngredientPayload();
            ip.setName("Ingredients from source link");
            ip.setQuantity("See source");
            ip.setIcon("🍳");
            ingredients.add(ip);
        }

        if (steps.isEmpty()) {
            steps.add("Open the source link to follow complete preparation steps: " + url);
        }

        if (image == null || image.isEmpty()) {
            image = "https://images.unsplash.com/photo-1495521821757-a1efb6729352?auto=format&fit=crop&w=800&q=80";
        }

        CreateRecipeRequest req = new CreateRecipeRequest();
        req.setName(title);
        req.setImage(image);
        req.setCookTime(15);
        req.setPrepTime(10);
        req.setKcal(350);
        req.setServings(2);
        req.setSourceUrl(url);
        req.setOrigin("IMPORT");
        req.setIngredients(ingredients);
        req.setSteps(steps);
        req.setEquipment(new ArrayList<>());
        return req;
    }

    private JsonNode findRecipeNodeInJsonLd(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode res = findRecipeNodeInJsonLd(child);
                if (res != null) return res;
            }
        } else if (node.isObject()) {
            if (node.has("@type")) {
                String type = node.get("@type").asText();
                if ("Recipe".equalsIgnoreCase(type) || type.toLowerCase().contains("recipe")) {
                    return node;
                }
            }
            if (node.has("@graph") && node.get("@graph").isArray()) {
                return findRecipeNodeInJsonLd(node.get("@graph"));
            }
        }
        return null;
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
            prefs.put("budget", user.getGroceryBudget());
            prefs.put("goals", user.getOnboardingGoals());
            prefs.put("grocery_frequency", user.getGroceryFrequency());
            prefs.put("grocery_stores", user.getGroceryStores());
            prefs.put("excited_features", user.getExcitedFeatures());
            String systemInstructions = "Be extremely precise and generous in the 'tips' (notes and advice) section for each recipe. Include advice on texture, flavor variations, and storage. "
                    + "STRICT REQUIREMENT: You MUST ONLY generate recipes using the provided list of ingredients. "
                    + "DO NOT add or suggest other main ingredients or extra ingredients in the recipe creation. "
                    + "Respect the list of ingredients strictly. If the provided ingredients are not sufficient or suitable "
                    + "to make at least one realistic recipe, do not invent or add other ingredients; instead, generate "
                    + "absolutely zero recipes (an empty recipes array).";
            prefs.put("system_instructions", systemInstructions);

            Map<String, Object> body = new HashMap<>();
            body.put("ingredients", request.getIngredients());
            body.put("user_preferences", prefs);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, getInternalHeaders());
            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/generate", requestEntity, ScanResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<CreateRecipeRequest> recipes = response.getBody().getRecipes();
                if (recipes == null || recipes.isEmpty()) {
                    throw new BadRequestException("The provided ingredients are not sufficient to generate any recipe. Please add more ingredients.");
                }
                for (CreateRecipeRequest r : recipes) r.setOrigin("MANUAL");
                assignBestMatchingImages(recipes);
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
            prefs.put("budget", user.getGroceryBudget());
            prefs.put("goals", user.getOnboardingGoals());
            prefs.put("grocery_frequency", user.getGroceryFrequency());
            prefs.put("grocery_stores", user.getGroceryStores());
            prefs.put("excited_features", user.getExcitedFeatures());
            prefs.put("system_instructions", "Be extremely precise and generous in the 'tips' (notes and advice) section for each recipe. Include advice on texture, flavor variations, and storage.");
            
            body.add("user_preferences", objectMapper.writeValueAsString(prefs));

            HttpHeaders headers = getInternalHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/image", requestEntity, ScanResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ScanResponse res = response.getBody();
                if (res.getRecipes() != null) {
                    for (CreateRecipeRequest r : res.getRecipes()) {
                        r.setOrigin("SCAN");
                    }
                    assignBestMatchingImages(res.getRecipes());
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
            prefs.put("budget", user.getGroceryBudget());
            prefs.put("goals", user.getOnboardingGoals());
            prefs.put("grocery_frequency", user.getGroceryFrequency());
            prefs.put("grocery_stores", user.getGroceryStores());
            prefs.put("excited_features", user.getExcitedFeatures());

            // Build an ultra-strict prompt that names the exact allowed ingredients
            String ingredientList = String.join(", ", ingredients);
            String systemInstructions =
                "Be extremely precise and generous in the 'tips' (notes and advice) section for each recipe. "
                + "Include advice on texture, flavor variations, and storage. "
                + "=== ABSOLUTE STRICT RULE === "
                + "The ONLY ingredients you are allowed to use are EXACTLY the ones the user provided: [" + ingredientList + "]. "
                + "You MUST NOT add, suggest, or assume any other ingredient — including basic pantry staples like oil, salt, pepper, water, butter, garlic, onion, or spices — "
                + "UNLESS they are explicitly listed by the user. "
                + "Do NOT modify, expand, or supplement this ingredient list in any way. "
                + "Every ingredient used in every recipe step MUST appear in the user's provided list. "
                + "If the provided ingredients cannot form at least one realistic, complete recipe on their own, "
                + "you MUST return an empty recipes array (zero recipes). Do NOT invent or add missing ingredients to compensate. "
                + "Generate between 1 and 6 recipes maximum. Do not exceed 6 recipes under any circumstance.";
            prefs.put("system_instructions", systemInstructions);

            Map<String, Object> body = new HashMap<>();
            body.put("ingredients", ingredients);
            body.put("user_preferences", prefs);

            log.info("ScanTyped: sending {} ingredients to AI: {}", ingredients.size(), ingredientList);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, getInternalHeaders());
            ResponseEntity<ScanResponse> response = restTemplate.postForEntity(baseUrl + "/api/recipes/generate", requestEntity, ScanResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ScanResponse res = response.getBody();

                // Validate: must have at least 1 recipe
                if (res.getRecipes() == null || res.getRecipes().isEmpty()) {
                    throw new BadRequestException(
                        "The ingredients you provided are not sufficient to create any recipe. " +
                        "Please add more ingredients to get recipe suggestions.");
                }

                // Enforce max 6 recipes
                if (res.getRecipes().size() > 6) {
                    res.setRecipes(res.getRecipes().subList(0, 6));
                }

                // Tag all results as SCAN origin and assign images
                for (CreateRecipeRequest r : res.getRecipes()) {
                    r.setOrigin("SCAN");
                }
                assignBestMatchingImages(res.getRecipes());

                // Populate allowed_ingredients if the microservice didn't return them
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

                log.info("ScanTyped: returning {} recipe(s) for ingredients: {}", res.getRecipes().size(), ingredientList);
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
    public AiIngredientDetectionResponse validateTypedIngredients(List<String> ingredients) {
        List<com.cooked.backend.dto.request.IngredientPayload> allowed = new ArrayList<>();
        
        for (String ingName : ingredients) {
            com.cooked.backend.dto.request.IngredientPayload p = new com.cooked.backend.dto.request.IngredientPayload();
            p.setName(ingName);
            p.setQuantity("-");
            
            // Fast direct database lookup (no AI wait time)
            java.util.Optional<com.cooked.backend.entity.Ingredient> dbIng = ingredientRepository.findFirstByName(ingName);
            if (dbIng.isPresent() && dbIng.get().getIcon() != null) {
                p.setIcon(dbIng.get().getIcon());
            } else {
                // Try searching with containing text
                List<com.cooked.backend.entity.Ingredient> matches = ingredientRepository.findByNameContainingIgnoreCase(ingName);
                if (!matches.isEmpty() && matches.get(0).getIcon() != null) {
                    p.setIcon(matches.get(0).getIcon());
                } else {
                    p.setIcon("🛒"); // generic fallback icon
                }
            }
            allowed.add(p);
        }
        
        AiIngredientDetectionResponse response = new AiIngredientDetectionResponse();
        response.setAllowed_ingredients(allowed);
        response.setRestricted_ingredients(new ArrayList<>());
        
        return response;
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

            HttpHeaders headers = getInternalHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<AiIngredientDetectionResponse> response = restTemplate.postForEntity(
                baseUrl + "/api/image", 
                requestEntity, 
                AiIngredientDetectionResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AiIngredientDetectionResponse res = response.getBody();
                if (res.getAllowed_ingredients() != null) {
                    for (com.cooked.backend.dto.request.IngredientPayload p : res.getAllowed_ingredients()) {
                        java.util.Optional<com.cooked.backend.entity.Ingredient> dbIng = ingredientRepository.findFirstByName(p.getName());
                        if (dbIng.isPresent() && dbIng.get().getIcon() != null) {
                            p.setIcon(dbIng.get().getIcon());
                        } else {
                            List<com.cooked.backend.entity.Ingredient> matches = ingredientRepository.findByNameContainingIgnoreCase(p.getName());
                            if (!matches.isEmpty() && matches.get(0).getIcon() != null) {
                                p.setIcon(matches.get(0).getIcon());
                            }
                        }
                    }
                }
                return res;
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

    private void assignBestMatchingImages(List<CreateRecipeRequest> recipes) {
        if (recipes == null) return;

        for (CreateRecipeRequest request : recipes) {
            if (request.getImage() != null && !request.getImage().isEmpty()
                    && !request.getImage().contains("unsplash") && !request.getImage().contains("splash")) {
                continue;
            }

            String name = request.getName();
            if (name == null || name.isEmpty()) continue;

            // --- Build keyword list (meaningful words only, sorted longest first) ---
            String[] words = name.split("\\W+");
            List<String> keywords = new ArrayList<>();
            for (String w : words) {
                if (w.length() > 3 && !STOP_WORDS.contains(w.toLowerCase())) {
                    keywords.add(w.toLowerCase());
                }
            }
            keywords.sort((a, b) -> Integer.compare(b.length(), a.length()));

            if (keywords.isEmpty()) {
                // Nothing useful to search on — skip to cuisine/category fallbacks below
            } else {
                // --- Phase 1: multi-keyword scoring (one DB round-trip) ---
                // Pad to exactly 5 slots (query requires 5 named params)
                List<String> kws = new ArrayList<>(keywords.subList(0, Math.min(keywords.size(), 5)));
                while (kws.size() < 5) kws.add("__NO_MATCH__");

                List<com.cooked.backend.entity.Recipe> candidates = recipeRepository.findExploreRecipesByKeywords(
                        kws.get(0), kws.get(1), kws.get(2), kws.get(3), kws.get(4));

                // Score each candidate: count how many keywords appear in its name
                com.cooked.backend.entity.Recipe bestMatch = null;
                int bestScore = 0;

                for (com.cooked.backend.entity.Recipe candidate : candidates) {
                    if (candidate.getImage() == null || candidate.getImage().isEmpty()) continue;
                    String candidateName = candidate.getName().toLowerCase();
                    int score = 0;
                    for (String kw : keywords) {
                        if (candidateName.contains(kw)) score++;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestMatch = candidate;
                    }
                }

                if (bestMatch != null) {
                    request.setImage(bestMatch.getImage());
                    log.info("Assigned image for '{}' via multi-keyword match (score={}, matched='{}')",
                            name, bestScore, bestMatch.getName());
                    continue; // Move to the next recipe
                }

                // --- Phase 2: single-keyword fallback (try each keyword individually) ---
                boolean found = false;
                for (String keyword : keywords) {
                    List<com.cooked.backend.entity.Recipe> matches = recipeRepository.findByNameContainingIgnoreCase(keyword);
                    for (com.cooked.backend.entity.Recipe match : matches) {
                        if (match.getOrigin() == com.cooked.backend.entity.RecipeOrigin.EXPLORE
                                && match.getImage() != null && !match.getImage().isEmpty()
                                && !match.getImage().contains("unsplash") && !match.getImage().contains("splash")) {
                            request.setImage(match.getImage());
                            log.info("Assigned image for '{}' via single-keyword fallback (keyword='{}')", name, keyword);
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
                if (found) continue;
            }

            // --- Phase 3: cuisine fallback ---
            boolean found = false;
            if (request.getCuisine() != null && !request.getCuisine().isEmpty()) {
                org.springframework.data.domain.Page<com.cooked.backend.entity.Recipe> cuisineMatches =
                        recipeRepository.findByCuisineWithImage(request.getCuisine(),
                                org.springframework.data.domain.PageRequest.of(0, 1));
                if (cuisineMatches.hasContent()) {
                    request.setImage(cuisineMatches.getContent().get(0).getImage());
                    log.info("Assigned image for '{}' via cuisine fallback (cuisine='{}')", name, request.getCuisine());
                    found = true;
                }
            }

            // --- Phase 4: category fallback ---
            if (!found && request.getCategories() != null && !request.getCategories().isEmpty()) {
                org.springframework.data.domain.Page<com.cooked.backend.entity.Recipe> categoryMatches =
                        recipeRepository.findByCategoryWithImage(request.getCategories().get(0),
                                org.springframework.data.domain.PageRequest.of(0, 1));
                if (categoryMatches.hasContent()) {
                    request.setImage(categoryMatches.getContent().get(0).getImage());
                    log.info("Assigned image for '{}' via category fallback (category='{}')", name, request.getCategories().get(0));
                }
            }
        }
    }


    private void verifyAiAccess(User user) {
        if (!subscriptionService.hasAiAccess(user)) {
            throw new PaymentRequiredException("AI access requires a premium subscription or trial.");
        }
    }

    @Override
    public java.util.Map<String, Double> estimateIngredientPrices(List<String> ingredients) {
        return java.util.Collections.emptyMap();
    }
}
