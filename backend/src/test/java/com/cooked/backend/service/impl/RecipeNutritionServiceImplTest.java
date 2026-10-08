package com.cooked.backend.service.impl;

import com.cooked.backend.entity.Ingredient;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeIngredient;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.askcooked.ChatCompletionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecipeNutritionServiceImplTest {

    private final ObjectMapper json = new ObjectMapper();

    private ChatCompletionClient answering(String content, AtomicReference<ObjectNode> sent) {
        return new ChatCompletionClient() {
            public boolean isConfigured() { return true; }
            public JsonNode complete(ObjectNode request) {
                sent.set(request);
                ObjectNode r = json.createObjectNode();
                r.putArray("choices").addObject().putObject("message").put("content", content);
                return r;
            }
        };
    }

    @Test
    void savesPerServingValuesAndKeepsAnExistingKcal() {
        UUID id = UUID.randomUUID();
        RecipeIngredient rice = new RecipeIngredient();
        rice.setIngredient(Ingredient.builder().name("Rice").build());
        rice.setQuantity("200 g");
        Recipe r = Recipe.builder().id(id).name("Thieb").servings(4).kcal(550).recipeIngredients(Set.of(rice)).build();
        RecipeRepository repo = mock(RecipeRepository.class);
        when(repo.findById(id)).thenReturn(Optional.of(r));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        AtomicReference<ObjectNode> sent = new AtomicReference<>();

        var out = new RecipeNutritionServiceImpl(repo, answering("{\"kcal\":480,\"protein_g\":24.26,\"carbs_g\":60,\"fat_g\":-1,\"fiber_g\":\"x\",\"sodium_mg\":812.6}", sent), json, "gpt-4o-mini").estimate(id);

        assertEquals(550, out.kcal());
        assertFalse(out.kcalUpdated());
        assertEquals(24.3, r.getProteinG());
        assertEquals(60.0, r.getCarbsG());
        assertNull(r.getFatG());          // negative refused
        assertNull(r.getFiberG());        // not a number
        assertEquals(813, r.getSodiumMg());
        assertTrue(sent.get().toString().contains("200 g Rice"));
        assertTrue(sent.get().toString().contains("Servings: 4"));
    }

    @Test
    void needsIngredientsAndAReadableAnswer() {
        UUID id = UUID.randomUUID();
        RecipeRepository repo = mock(RecipeRepository.class);
        when(repo.findById(id)).thenReturn(Optional.of(Recipe.builder().id(id).name("Empty").recipeIngredients(Set.of()).build()));
        var service = new RecipeNutritionServiceImpl(repo, answering("not json", new AtomicReference<>()), json, "m");
        assertThrows(BadRequestException.class, () -> service.estimate(id));
        assertThrows(BadRequestException.class, () -> service.parse(json.createObjectNode()));
    }
}
