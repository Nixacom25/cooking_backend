package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.service.askcooked.ChatCompletionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecipeNutritionKcalTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ChatCompletionClient chat = mock(ChatCompletionClient.class);
    private final RecipeNutritionServiceImpl service = new RecipeNutritionServiceImpl(null, chat, json, "gpt-4o-mini");

    private static CreateRecipeRequest recipe(String name, Integer kcal) {
        CreateRecipeRequest r = new CreateRecipeRequest();
        r.setName(name);
        r.setKcal(kcal);
        r.setServings(2);
        r.setIngredients(List.of(IngredientPayload.builder().name("chicken").quantity("300 g").build()));
        return r;
    }

    private void aiAnswers(String content) throws Exception {
        when(chat.isConfigured()).thenReturn(true);
        when(chat.complete(any())).thenReturn(json.readTree(
                "{\"choices\":[{\"message\":{\"content\":" + json.writeValueAsString(content) + "}}]}"));
    }

    @Test
    void fillsOnlyMissingOrZeroKcalInOneCall() throws Exception {
        aiAnswers("{\"kcal\":[510, 380]}");
        CreateRecipeRequest zero = recipe("Tacos", 0);
        CreateRecipeRequest kept = recipe("Salad", 220);
        CreateRecipeRequest none = recipe("Curry", null);

        service.fillMissingKcal(List.of(zero, kept, none));

        assertEquals(510, zero.getKcal());
        assertEquals(220, kept.getKcal());
        assertEquals(380, none.getKcal());
        verify(chat, times(1)).complete(any());
    }

    @Test
    void aiFailureNeverThrows() throws Exception {
        when(chat.isConfigured()).thenReturn(true);
        when(chat.complete(any())).thenThrow(new RuntimeException("timeout"));
        CreateRecipeRequest r = recipe("Tacos", 0);

        assertDoesNotThrow(() -> service.fillMissingKcal(List.of(r)));
        assertEquals(0, r.getKcal());
    }

    @Test
    void zeroFromAiIsIgnored() throws Exception {
        aiAnswers("{\"kcal\":[0]}");
        CreateRecipeRequest r = recipe("Water", null);
        service.fillMissingKcal(List.of(r));
        assertNull(r.getKcal());
    }
}
