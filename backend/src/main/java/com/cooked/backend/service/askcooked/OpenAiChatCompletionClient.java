package com.cooked.backend.service.askcooked;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OpenAiChatCompletionClient implements ChatCompletionClient {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public OpenAiChatCompletionClient(RestTemplate restTemplate, ObjectMapper objectMapper, @Value("${openai.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty() && !apiKey.startsWith("sk-placeholder");
    }

    @Override
    public JsonNode complete(ObjectNode request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = restTemplate.postForObject(URL, new HttpEntity<>(request.toString(), headers), String.class);
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable OpenAI response", e);
        }
    }
}
