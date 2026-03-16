package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarkhorAiServiceImpl implements AiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @Value("${ai.api.base-url:https://recipe.markhorsystems.com}")
    private String baseUrl;

    @Override
    public CreateRecipeRequest extractRecipeFromLink(String url, String email) {
        // This is not supported by Markhor API yet, would typically fallback to OpenAI
        // For now, throwing not implemented or using existing logic if possible
        throw new UnsupportedOperationException("Link extraction not supported by Markhor AI yet.");
    }

    @Override
    public AiIngredientDetectionResponse detectIngredients(MultipartFile file, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Image part
            ByteArrayResource imageResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("image", imageResource);

            // User preferences part (JSON-stringified)
            Map<String, Object> prefs = Map.of(
                    "allergies", user.getAllergies(),
                    "preferences", user.getDietaryPreferences());
            body.add("user_preferences", objectMapper.writeValueAsString(prefs));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<AiIngredientDetectionResponse> response = restTemplate.postForEntity(
                    baseUrl + "/api/ingredients/detect",
                    requestEntity,
                    AiIngredientDetectionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new BadRequestException("Failed to detect ingredients from AI.");

        } catch (IOException e) {
            throw new BadRequestException("Could not process image: " + e.getMessage());
        }
    }

    @Override
    public List<CreateRecipeRequest> generateRecipes(AiRecipeGenerationRequest request, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AiRecipeGenerationRequest> requestEntity = new HttpEntity<>(request, headers);

        ResponseEntity<List<CreateRecipeRequest>> response = restTemplate.exchange(
                baseUrl + "/api/recipes/generate",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<List<CreateRecipeRequest>>() {
                });

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        }

        return Collections.emptyList();
    }
}
