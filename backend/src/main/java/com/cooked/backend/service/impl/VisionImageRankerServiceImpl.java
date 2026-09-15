package com.cooked.backend.service.impl;

import com.cooked.backend.service.VisionImageRankerService;
import com.cooked.backend.service.dto.RecipeTags;
import com.cooked.backend.service.dto.VisionRankResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VisionImageRankerServiceImpl implements VisionImageRankerService {

    private static final Logger log = LoggerFactory.getLogger(VisionImageRankerServiceImpl.class);
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String VISION_MODEL = "gpt-4o-mini";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Override
    public VisionRankResult rankCandidates(String recipeTitle, List<String> candidateImageUrls) {
        if (candidateImageUrls == null || candidateImageUrls.isEmpty()) {
            return VisionRankResult.builder().bestIndex(-1).confidence(0).reason("No candidates").build();
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Recipe title: \"").append(recipeTitle).append("\".\n");
        prompt.append("You are shown ").append(candidateImageUrls.size())
                .append(" candidate food photos, indexed starting at 0 in the order given.\n");
        prompt.append("Pick the single photo that most accurately depicts this exact dish. ")
                .append("Do not pick an image just because it looks appetizing or vaguely similar - ")
                .append("it must plausibly BE this dish.\n");
        prompt.append("Respond with raw JSON only: {\"bestIndex\": <int, -1 if none are a real match>, ")
                .append("\"confidence\": <float 0.0-1.0>, \"reason\": \"<short reason>\"}");

        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt.toString()));
        for (String url : candidateImageUrls) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
        }

        try {
            String raw = callOpenAiVision(content);
            JsonNode json = objectMapper.readTree(raw);
            return VisionRankResult.builder()
                    .bestIndex(json.path("bestIndex").asInt(-1))
                    .confidence(json.path("confidence").asDouble(0))
                    .reason(json.path("reason").asText(""))
                    .build();
        } catch (Exception e) {
            log.warn("Vision ranking failed for '{}': {}", recipeTitle, e.getMessage());
            return VisionRankResult.builder().bestIndex(-1).confidence(0).reason("Vision call failed").build();
        }
    }

    @Override
    public RecipeTags tagImage(String imageUrl, String recipeContextName) {
        String prompt = "This photo is currently associated with the dish \"" + recipeContextName + "\". "
                + "Look at the photo and describe it with structured tags. "
                + "Respond with raw JSON only: {\"cuisine\": \"<or null>\", \"protein\": \"<or null>\", "
                + "\"dishType\": \"<e.g. rice bowl, soup, salad, pasta - or null>\", "
                + "\"cookingStyle\": \"<e.g. grilled, baked, fried - or null>\", "
                + "\"ingredients\": [\"<visible main ingredients, lowercase>\"]}";

        List<Object> content = List.of(
                Map.of("type", "text", "text", prompt),
                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
        );

        try {
            String raw = callOpenAiVision(content);
            JsonNode json = objectMapper.readTree(raw);
            List<String> ingredients = new ArrayList<>();
            json.path("ingredients").forEach(n -> ingredients.add(n.asText()));
            return RecipeTags.builder()
                    .cuisine(nullIfBlank(json.path("cuisine").asText(null)))
                    .protein(nullIfBlank(json.path("protein").asText(null)))
                    .dishType(nullIfBlank(json.path("dishType").asText(null)))
                    .cookingStyle(nullIfBlank(json.path("cookingStyle").asText(null)))
                    .mainIngredients(ingredients)
                    .build();
        } catch (Exception e) {
            log.warn("Vision tagging failed for image '{}': {}", imageUrl, e.getMessage());
            return RecipeTags.builder().build();
        }
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || s.equalsIgnoreCase("null")) ? null : s;
    }

    private String callOpenAiVision(List<Object> content) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        Map<String, Object> requestBody = Map.of(
                "model", VISION_MODEL,
                "messages", List.of(Map.of("role", "user", "content", content)),
                "response_format", Map.of("type", "json_object"),
                "max_tokens", 500,
                "temperature", 0.1);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        }
        throw new IllegalStateException("OpenAI vision call failed: " + response.getStatusCode());
    }
}
