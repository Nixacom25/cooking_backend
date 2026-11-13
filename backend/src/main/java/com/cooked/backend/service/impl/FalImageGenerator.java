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
import java.util.Map;

/**
 * fal.ai text-to-image (FLUX.2 [klein] 9B by default) for client recipe photos: about 1 s per
 * photo and a fraction of a cent, against ~14 s for gpt-image (benchmark of 2026-11-13).
 * The key (FAL_KEY) stays server-side; without it {@link #available()} is false.
 */
@Slf4j
@Service("falImageGenerator")
public class FalImageGenerator implements AiImageGenerator {

    static final String BASE_URL = "https://fal.run/";
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int size;
    private final double costPerMegapixel;

    @org.springframework.beans.factory.annotation.Autowired
    public FalImageGenerator(ObjectMapper objectMapper,
                             @Value("${fal.api-key:}") String apiKey,
                             @Value("${client.ai-images.model:fal-ai/flux-2/klein/9b}") String model,
                             @Value("${client.ai-images.size:1024}") int size,
                             @Value("${client.ai-images.cost-per-megapixel:0.006}") double costPerMegapixel) {
        this(objectMapper, BASE_URL, apiKey, model, size, costPerMegapixel);
    }

    FalImageGenerator(ObjectMapper objectMapper, String baseUrl, String apiKey, String model, int size, double costPerMegapixel) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.size = size;
        this.costPerMegapixel = costPerMegapixel;
    }

    @Override
    public String provider() {
        return "fal";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String quality() {
        return size + "x" + size;
    }

    @Override
    public boolean available() {
        return !apiKey.isEmpty();
    }

    @Override
    public GeneratedImage generate(String prompt) throws IOException {
        if (!available()) throw new IOException("FAL_KEY is not configured");
        String body = objectMapper.writeValueAsString(Map.of(
                "prompt", prompt,
                "image_size", Map.of("width", size, "height", size),
                "num_images", 1));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + model))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("fal " + model + " HTTP " + res.statusCode() + ": " + abbreviate(res.body()));
        }
        JsonNode image = objectMapper.readTree(res.body()).path("images").path(0);
        String url = image.path("url").asText("");
        if (url.isBlank()) throw new IOException("fal returned no image: " + abbreviate(res.body()));

        HttpResponse<byte[]> file = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (file.statusCode() / 100 != 2 || file.body().length == 0 || file.body().length > MAX_BYTES) {
            throw new IOException("fal image download failed: HTTP " + file.statusCode());
        }
        int w = image.path("width").asInt(size);
        int h = image.path("height").asInt(size);
        double cost = Math.round(costPerMegapixel * (w * (double) h / 1_000_000) * 10_000) / 10_000.0;
        return new GeneratedImage(file.body(), model, cost);
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return http.send(req, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("fal call interrupted", e);
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
