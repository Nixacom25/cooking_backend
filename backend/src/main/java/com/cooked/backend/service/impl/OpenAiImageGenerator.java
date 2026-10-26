package com.cooked.backend.service.impl;

import com.cooked.backend.service.AiImageGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI Images API. Tries the configured model, then each fallback model in
 * order, so a model OpenAI retires (gpt-image-1 and dall-e-3 were) never
 * leaves creators without photos. A parameter the API stops accepting is
 * dropped and the call retried.
 */
@Slf4j
@Service
public class OpenAiImageGenerator implements AiImageGenerator {

    static final String ENDPOINT = "https://api.openai.com/v1/images/generations";
    private static final Pattern UNKNOWN_PARAM = Pattern.compile("(?i)unknown parameter: '([a-z_]+)'");
    /** Parameters a call can do without; "model", "prompt" and "n" are never dropped. */
    private static final Set<String> OPTIONAL = Set.of("quality", "size", "output_format", "output_compression", "style", "response_format");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final String quality;
    private final String size;
    private final double costPerImage;
    private final List<String> fallbackModels;
    private final double fallbackCostPerImage;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiImageGenerator(ObjectMapper objectMapper,
                                @Value("${openai.api.key}") String apiKey,
                                @Value("${creator.ai-images.model:gpt-image-1.5}") String model,
                                @Value("${creator.ai-images.quality:medium}") String quality,
                                @Value("${creator.ai-images.size:1024x1024}") String size,
                                @Value("${creator.ai-images.cost-per-image:0.034}") double costPerImage,
                                @Value("${creator.ai-images.fallback-models:gpt-image-2,gpt-image-1.5,gpt-image-1-mini}") String fallbackModels,
                                @Value("${creator.ai-images.fallback-cost-per-image:0.05}") double fallbackCostPerImage) {
        this(objectMapper, ENDPOINT, apiKey, model, quality, size, costPerImage, fallbackModels, fallbackCostPerImage);
    }

    OpenAiImageGenerator(ObjectMapper objectMapper, String endpoint, String apiKey, String model, String quality,
                         String size, double costPerImage, String fallbackModels, double fallbackCostPerImage) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.quality = quality;
        this.size = size;
        this.costPerImage = costPerImage;
        this.fallbackModels = Arrays.stream(fallbackModels.split(","))
                .map(String::trim).filter(m -> !m.isEmpty()).toList();
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
        Set<String> models = new LinkedHashSet<>();
        if (model != null && !model.isBlank()) models.add(model.trim());
        models.addAll(fallbackModels);

        IOException last = null;
        for (String m : models) {
            try {
                return new GeneratedImage(call(m, prompt), m, m.equals(model) ? costPerImage : fallbackCostPerImage);
            } catch (IOException e) {
                if (e instanceof Refused) throw e;
                log.warn("Image model {} failed, trying the next one: {}", m, e.getMessage());
                last = e;
            }
        }
        throw last != null ? last : new IOException("No image model configured");
    }

    /** OpenAI refused the prompt itself (safety system): another model won't help. */
    static final class Refused extends IOException {
        Refused(String message) {
            super(message);
        }
    }

    private byte[] call(String m, String prompt) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", m);
        payload.put("prompt", prompt);
        payload.put("n", 1);
        payload.put("size", size);
        payload.put("quality", quality);
        payload.put("output_format", "jpeg");
        payload.put("output_compression", 85);

        List<String> dropped = new ArrayList<>();
        while (true) {
            HttpResponse<String> response = send(payload);
            if (response.statusCode() == 200) {
                if (!dropped.isEmpty()) log.warn("Image model {} works without {}: update the payload", m, dropped);
                return imageBytes(response.body());
            }
            String error = errorMessage(response.body());
            Matcher unknown = UNKNOWN_PARAM.matcher(error);
            if (response.statusCode() == 400 && unknown.find() && OPTIONAL.contains(unknown.group(1))
                    && payload.remove(unknown.group(1)) != null) {
                dropped.add(unknown.group(1));
                continue;
            }
            String message = "OpenAI images " + m + " " + response.statusCode() + ": " + error;
            if (error.toLowerCase(java.util.Locale.ROOT).contains("safety")) throw new Refused(message);
            throw new IOException(message);
        }
    }

    private HttpResponse<String> send(Map<String, Object> payload) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(150))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image generation interrupted", e);
        }
    }

    /** The image as base64 in the response, or downloaded when the API gives a URL. */
    private byte[] imageBytes(String body) throws IOException {
        JsonNode data = objectMapper.readTree(body).path("data").path(0);
        String b64 = data.path("b64_json").asText("");
        if (!b64.isBlank()) return Base64.getDecoder().decode(b64);
        String url = data.path("url").asText("");
        if (url.isBlank()) throw new IOException("OpenAI images: empty response");
        try {
            HttpResponse<byte[]> image = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (image.statusCode() != 200 || image.body().length == 0) {
                throw new IOException("OpenAI images: could not download the image (" + image.statusCode() + ")");
            }
            return image.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image download interrupted", e);
        }
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
