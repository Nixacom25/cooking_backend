package com.cooked.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngredientKeysTest {

    @Test
    void keysIgnoreCaseAccentsPunctuationAndSimplePlurals() {
        assertEquals("netetou", IngredientKeys.key("Nététou"));
        assertEquals("green_onion", IngredientKeys.key("  Green Onions "));
        assertEquals("tomato", IngredientKeys.key("Tomatoes"));
        assertEquals("berry", IngredientKeys.key("berries"));
        assertEquals("chickpea", IngredientKeys.key("chickpeas"));
        assertEquals("huile_d_olive", IngredientKeys.key("Huile d'olive"));
        assertEquals("couscous", IngredientKeys.key("couscous"));
        assertEquals("hummus", IngredientKeys.key("Hummus"));
        assertEquals("bell_pepper", IngredientKeys.key("bell_pepper"));
        assertEquals("", IngredientKeys.key("  -- "));
    }

    @Test
    void canonicalIdsAreSnakeCase() {
        assertTrue(IngredientKeys.isCanonicalId("ground_chicken"));
        assertTrue(IngredientKeys.isCanonicalId("ube"));
        assertFalse(IngredientKeys.isCanonicalId("Ground_chicken"));
        assertFalse(IngredientKeys.isCanonicalId("ground__chicken"));
        assertFalse(IngredientKeys.isCanonicalId("2eggs"));
    }

    @Test
    void similarityFavoursSharedWords() {
        assertEquals(1.0, IngredientKeys.similarity("tomato", "tomato"));
        assertTrue(IngredientKeys.similarity("scallion", "scalion") > 0.8);
        assertTrue(IngredientKeys.similarity("cherry_tomato", "tomato") >= 0.8);
        assertTrue(IngredientKeys.similarity("tomato", "paprika") < 0.6);
    }
}
