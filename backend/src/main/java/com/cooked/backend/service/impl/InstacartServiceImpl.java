package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.InstacartIngredientDto;
import com.cooked.backend.dto.request.InstacartShoppingListRequest;
import com.cooked.backend.dto.response.InstacartLinkResponse;
import com.cooked.backend.entity.GroceryItem;
import com.cooked.backend.service.InstacartService;
import com.cooked.backend.util.IngredientNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InstacartServiceImpl implements InstacartService {

    @Value("${instacart.api.key:instacart_dev_key}")
    private String apiKey;

    @Value("${instacart.api.url:https://api.instacart.com/v2}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public InstacartServiceImpl() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public InstacartLinkResponse createShoppableList(List<GroceryItem> groceryItems) {
        if (groceryItems == null || groceryItems.isEmpty()) {
            throw new IllegalArgumentException("Grocery list is empty");
        }

        List<InstacartIngredientDto> normalizedIngredients = new ArrayList<>();

        for (GroceryItem item : groceryItems) {
            String name = item.getIngredient() != null ? item.getIngredient().getName() : null;
            String quantity = item.getQuantity();

            if (name != null && !name.trim().isEmpty()) {
                InstacartIngredientDto dto = IngredientNormalizer.normalize(name, quantity);
                if (dto != null && dto.getName() != null && !dto.getName().trim().isEmpty()) {
                    normalizedIngredients.add(dto);
                }
            }
        }

        if (normalizedIngredients.isEmpty()) {
            throw new IllegalArgumentException("No valid ingredients found in grocery list");
        }

        InstacartShoppingListRequest request = InstacartShoppingListRequest.builder()
                .title("Cooked Grocery List")
                .landingPageTitle("Cooked Grocery List")
                .ingredients(normalizedIngredients)
                .build();

        String webUrl = null;
        String deepLinkUrl = null;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-Instacart-Api-Key", apiKey);

            HttpEntity<InstacartShoppingListRequest> entity = new HttpEntity<>(request, headers);
            String endpoint = apiUrl.endsWith("/") ? apiUrl + "shoppable_lists" : apiUrl + "/shoppable_lists";

            log.info("Sending Instacart API request to {} with {} ingredients", endpoint, normalizedIngredients.size());
            ResponseEntity<Map> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                if (body.containsKey("url")) {
                    webUrl = body.get("url").toString();
                } else if (body.containsKey("redirect_url")) {
                    webUrl = body.get("redirect_url").toString();
                } else if (body.containsKey("landing_page_url")) {
                    webUrl = body.get("landing_page_url").toString();
                }
                if (body.containsKey("deep_link_url")) {
                    deepLinkUrl = body.get("deep_link_url").toString();
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Instacart Developer API request failed or dev key used ({}), generating fallback shoppable URL: {}", apiKey, e.getMessage());
        }

        // Fallback shoppable URL generation if production API key is in setup phase
        if (webUrl == null || webUrl.isEmpty()) {
            String itemsParam = normalizedIngredients.stream()
                    .map(InstacartIngredientDto::getName)
                    .map(n -> URLEncoder.encode(n, StandardCharsets.UTF_8))
                    .collect(Collectors.joining(","));

            webUrl = "https://www.instacart.com/store/partner_collections/cooked?items=" + itemsParam;
        }

        if (deepLinkUrl == null || deepLinkUrl.isEmpty()) {
            deepLinkUrl = webUrl.replace("https://www.instacart.com/", "instacart://");
        }

        return InstacartLinkResponse.builder()
                .url(webUrl)
                .deepLinkUrl(deepLinkUrl)
                .itemCount(groceryItems.size())
                .matchedCount(normalizedIngredients.size())
                .message("Instacart shopping link generated successfully")
                .build();
    }
}
