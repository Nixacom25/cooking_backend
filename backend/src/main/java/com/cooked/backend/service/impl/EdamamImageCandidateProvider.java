package com.cooked.backend.service.impl;

import com.cooked.backend.service.ImageCandidateProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EdamamImageCandidateProvider implements ImageCandidateProvider {

    private static final Logger log = LoggerFactory.getLogger(EdamamImageCandidateProvider.class);
    private static final String EDAMAM_URL = "https://api.edamam.com/api/recipes/v2";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${edamam.app.id:}")
    private String appId;

    @Value("${edamam.app.key:}")
    private String appKey;

    @Override
    public List<String> searchCandidates(String query, int limit) {
        List<String> images = new ArrayList<>();
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank() || query == null || query.isBlank()) {
            // Not configured yet (or nothing to search on) - callers fall
            // back to the generic category/cuisine image instead of failing.
            return images;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(EDAMAM_URL)
                    .queryParam("type", "public")
                    .queryParam("q", query)
                    .queryParam("app_id", appId)
                    .queryParam("app_key", appKey)
                    .queryParam("field", "image")
                    .build()
                    .toUriString();

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return images;
            }

            JsonNode hits = objectMapper.readTree(response.getBody()).path("hits");
            for (JsonNode hit : hits) {
                String image = hit.path("recipe").path("image").asText(null);
                if (image != null && !image.isBlank()) {
                    images.add(image);
                }
                if (images.size() >= limit) break;
            }
        } catch (Exception e) {
            log.warn("Edamam image search failed for query '{}': {}", query, e.getMessage());
        }
        return images;
    }
}
