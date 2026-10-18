package com.cooked.backend.service.impl;

import com.cooked.backend.service.AiImageGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * OpenAI Images API. Uses the configured gpt-image model (compressed JPEG); when
 * the account can't use it (gpt-image needs a verified organization) it falls
 * back to DALL-E 3 so creators still get a photo.
 */
@Service
public class OpenAiImageGenerator implements AiImageGenerator {

    private static final String URL = "https://api.openai.com/v1/images/generations";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String quality;
    private final String size;
    private final double costPerImage;
    private final String fallbackModel;
    private final double fallbackCostPerImage;

    public OpenAiImageGenerator(ObjectMapper objectMapper,
                                @Value("${openai.api.key}") String apiKey,
                                @Value("${creator.ai-images.model:gpt-image-1}") String model,
                                @Value("${creator.ai-images.quality:medium}") String quality,
                                @Value("${creator.ai-images.size:1024x1024}") String size,
                                @Value("${creator.ai-images.cost-per-image:0.042}") double costPerImage,
                                @Value("${creator.ai-images.fallback-model:dall-e-3}") String fallbackModel,
                                @Value("${creator.ai-images.fallback-cost-per-image:0.04}") double fallbackCostPerImage) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.quality = quality;
        this.size = size;
        this.costPerImage = costPerImage;
        this.fallbackModel = fallbackModel;
        this.fallbackCostPerImage = fallbackCostPerImage;
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String quality() {
        return quality;
    }

    @Override
    public GeneratedImage generate(String prompt) throws IOException {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "n", 1,
                "size", size,
                "quality", quality,
                "output_format", "jpeg",
                "output_compression", 85);
        try {
            return new GeneratedImage(call(body), model, costPerImage);
        } catch (ModelUnavailable e) {
            if (fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(model)) throw e;
            Map<String, Object> fallback = Map.of(
                    "model", fallbackModel,
                    "prompt", prompt.length() > 3900 ? prompt.substring(0, 3900) : prompt,
                    "n", 1,
                    "size", "1024x1024",
                    "quality", "standard",
                    "style", "natural",
                    "response_format", "b64_json");
            return new GeneratedImage(call(fallback), fallbackModel, fallbackCostPerImage);
        }
    }

    /** The account can't use this model (not verified / no access): worth trying the fallback. */
    static final class ModelUnavailable extends IOException {
        ModelUnavailable(String message) {
            super(message);
        }
    }

    private byte[] call(Map<String, Object> payload) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(150))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image generation interrupted", e);
        }
        if (response.statusCode() != 200) {
            String message = "OpenAI images " + payload.get("model") + " " + response.statusCode() + ": " + errorMessage(response.body());
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            boolean noAccess = response.statusCode() == 403 || response.statusCode() == 404
                    || lower.contains("verif") || lower.contains("does not exist") || lower.contains("not have access");
            throw noAccess ? new ModelUnavailable(message) : new IOException(message);
        }
        JsonNode image = objectMapper.readTree(response.body()).path("data").path(0).path("b64_json");
        if (image.isMissingNode() || image.asText().isBlank()) {
            throw new IOException("OpenAI images: empty response");
        }
        return Base64.getDecoder().decode(image.asText());
    }

    private String errorMessage(String body) {
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText("");
            return message.isBlank() ? "request failed" : message;
        } catch (Exception e) {
            return "request failed";
        }
    }
}
