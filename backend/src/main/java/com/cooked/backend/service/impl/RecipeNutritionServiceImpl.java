package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.NutritionEstimateResponse;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeIngredient;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.RecipeNutritionService;
import com.cooked.backend.service.askcooked.ChatCompletionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecipeNutritionServiceImpl implements RecipeNutritionService {

    private final RecipeRepository recipes;
    private final ChatCompletionClient chat;
    private final ObjectMapper json;
    private final String model;

    public RecipeNutritionServiceImpl(RecipeRepository recipes, ChatCompletionClient chat, ObjectMapper json,
                                      @Value("${ask-cooked.model:gpt-4o-mini}") String model) {
        this.recipes = recipes;
        this.chat = chat;
        this.json = json;
        this.model = model;
    }

    @Override
    @Transactional
    public NutritionEstimateResponse estimate(UUID recipeId) {
        Recipe r = recipes.findById(recipeId).orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
        if (!chat.isConfigured()) throw new BadRequestException("OPENAI_API_KEY is not configured on the server.");
        String ingredients = r.getRecipeIngredients() == null ? "" : r.getRecipeIngredients().stream()
                .filter(i -> i.getIngredient() != null)
                .map(RecipeNutritionServiceImpl::line)
                .collect(Collectors.joining("\n"));
        if (ingredients.isBlank()) throw new BadRequestException("Add ingredients first: the estimate is based on them.");

        JsonNode answer = parse(chat.complete(request(r, ingredients)));
        boolean fillKcal = r.getKcal() == null || r.getKcal() <= 0;
        if (fillKcal) r.setKcal(intOrNull(answer, "kcal"));
        r.setProteinG(decimal(answer, "protein_g"));
        r.setCarbsG(decimal(answer, "carbs_g"));
        r.setFatG(decimal(answer, "fat_g"));
        r.setFiberG(decimal(answer, "fiber_g"));
        r.setSodiumMg(intOrNull(answer, "sodium_mg"));
        recipes.save(r);
        return new NutritionEstimateResponse(r.getKcal(), r.getProteinG(), r.getCarbsG(), r.getFatG(), r.getFiberG(), r.getSodiumMg(), fillKcal);
    }

    static String line(RecipeIngredient i) {
        String q = i.getQuantity() == null ? "" : i.getQuantity().trim();
        return "- " + (q.isEmpty() ? "" : q + " ") + i.getIngredient().getName();
    }

    ObjectNode request(Recipe r, String ingredients) {
        ObjectNode req = json.createObjectNode();
        req.put("model", model);
        req.put("temperature", 0);
        req.putObject("response_format").put("type", "json_object");
        ArrayNode messages = req.putArray("messages");
        messages.addObject().put("role", "system").put("content",
                "You are a nutritionist. Estimate nutrition PER SERVING from the ingredient list. "
                        + "Answer only JSON: {\"kcal\":int,\"protein_g\":number,\"carbs_g\":number,\"fat_g\":number,\"fiber_g\":number,\"sodium_mg\":int}.");
        int servings = r.getServings() == null || r.getServings() < 1 ? 1 : r.getServings();
        messages.addObject().put("role", "user").put("content",
                "Recipe: " + r.getName() + "\nServings: " + servings + "\nIngredients:\n" + ingredients);
        return req;
    }

    JsonNode parse(JsonNode response) {
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        try {
            JsonNode node = json.readTree(content);
            if (node == null || !node.isObject()) throw new BadRequestException("The AI answer could not be read. Try again.");
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BadRequestException("The AI answer could not be read. Try again.");
        }
    }

    /** Non-negative number rounded to 0.1, or null. */
    static Double decimal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isNumber() || v.asDouble() < 0) return null;
        return Math.round(v.asDouble() * 10) / 10.0;
    }

    static Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.isNumber() || v.asDouble() < 0) return null;
        return (int) Math.round(v.asDouble());
    }
}
