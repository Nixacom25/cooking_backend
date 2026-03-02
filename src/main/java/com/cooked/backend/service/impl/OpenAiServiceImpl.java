package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.service.OpenAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAiServiceImpl implements OpenAiService {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    // Strict JSON schema for the AI to return
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

    private String buildUserPrompt(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null)
            return SYSTEM_PROMPT;

        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
        if (!user.getDietaryPreferences().isEmpty()) {
            sb.append(
                    "\nIMPORTANT USER PREFERENCES: Prioritize and adapt suggestions whenever possible for the following dietary preferences: ")
                    .append(String.join(", ", user.getDietaryPreferences())).append(".");
        }
        if (!user.getAllergies().isEmpty()) {
            sb.append("\nCRITICAL ALLERGIES: The user is allergic to the following ingredients: ")
                    .append(String.join(", ", user.getAllergies()))
                    .append(". MUST STRICTLY avoid suggesting or including these ingredients in the recipe output.");
        }
        return sb.toString();
    }

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
        try {
            // 1. Scrape the website text using JSoup
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
            String pageText = doc.body().text();

            // 2. Limit the text size to avoid huge token costs (take first 10,000
            // characters max)
            if (pageText.length() > 10000) {
                pageText = pageText.substring(0, 10000);
            }

            // 3. Send to OpenAI
            String customPrompt = buildUserPrompt(email);
            String responseJson = callOpenAi(pageText, false, customPrompt);
            return parseOpenAiResponse(responseJson);

        } catch (IOException e) {
            throw new BadRequestException("Could not scrape the provided URL: " + e.getMessage());
        }
    }

    @Override
    public CreateRecipeRequest extractRecipeFromImage(MultipartFile file, String email) {
        try {
            // 1. Convert Image to Base64
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());
            String dataUrl = "data:" + file.getContentType() + ";base64," + base64Image;

            // 2. Send to OpenAI Vision
            String customPrompt = buildUserPrompt(email);
            String responseJson = callOpenAi(dataUrl, true, customPrompt);
            return parseOpenAiResponse(responseJson);

        } catch (IOException e) {
            throw new BadRequestException("Could not process the provided image: " + e.getMessage());
        }
    }

    private String callOpenAi(String content, boolean isImage, String systemPrompt) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        Object userMessageContent;

        if (isImage) {
            userMessageContent = List.of(
                    Map.of("type", "text", "text", "Extract the recipe and ingredients from this image."),
                    Map.of("type", "image_url", "image_url", Map.of("url", content)));
        } else {
            userMessageContent = "Extract the recipe from the following text:\n\n" + content;
        }

        Map<String, Object> requestBody = Map.of(
                "model", isImage ? "gpt-4o" : "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessageContent)),
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

    private CreateRecipeRequest parseOpenAiResponse(String json) {
        try {
            // The JSON returned by OpenAI maps almost exactly to the DTO
            CreateRecipeRequest request = objectMapper.readValue(json, CreateRecipeRequest.class);
            return request;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("AI returned invalid JSON: " + e.getMessage());
        }
    }
}
