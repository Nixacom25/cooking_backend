package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.service.AiService;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private static final String SYSTEM_PROMPT = """
            You are an expert chef and recipe parser. Analyze the provided text or image and extract the recipe details into strictly format JSON matching the output structure exactly.
            Do not include any markdown formatting, only valid JSON.
            Output format:
            {
              "name": "Recipe Name",
              "cookTime": "30 mins",
              "kcal": 500,
              "ingredients": [
                {
                  "name": "ingredient name",
                  "quantity": "2 cups",
                  "icon": "🥕"
                }
              ],
              "steps": [
                "Chop the vegetables.",
                "Boil the water."
              ]
            }
            """;

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
            String pageText = doc.body().text();
            if (pageText.length() > 10000) {
                pageText = pageText.substring(0, 10000);
            }

            String responseJson = callOpenAi("Extract the recipe from the following text:\n\n" + pageText,
                    SYSTEM_PROMPT);
            return objectMapper.readValue(responseJson, CreateRecipeRequest.class);
        } catch (IOException e) {
            throw new BadRequestException("Could not scrape the provided URL: " + e.getMessage());
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
                "temperature", 0.0);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, request, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        }

        throw new BadRequestException("Failed to extract recipe from AI.");
    }
}
