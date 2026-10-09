package com.cooked.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngredientCategoriesTest {

    private static String of(String name) {
        return IngredientCategories.guess(IngredientKeys.key(name));
    }

    @Test
    void guessesFromWordsInOrder() {
        assertEquals("Common Spices", of("Black pepper"));
        assertEquals("Peppers", of("Red bell peppers"));
        assertEquals("Soups & Stocks", of("Chicken stock"));
        assertEquals("Poultry", of("Chicken thighs"));
        assertEquals("Sauces & Condiments", of("Tomato paste"));
        assertEquals("Tomatoes", of("Cherry tomatoes"));
        assertEquals("Vegetables", of("Eggplant"));
        assertEquals("Eggs", of("Eggs"));
        assertEquals("Nuts & Seeds", of("Peanut butter"));
        assertEquals("Common Spices", of("Nététou"));
        assertEquals("Other", of("Fufu"));
        assertEquals("Other", of("Half and half"));
        assertEquals("Dairy", of("Milk, whole milk"));
    }
}
