package com.cooked.backend.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeControllerImportSourceTest {

    @Test
    void importSourceKeepsOnlyTheDomain() {
        assertEquals("allrecipes.com", RecipeController.importSource("https://www.allrecipes.com/recipe/1/x?utm=1"));
        assertEquals("instagram.com", RecipeController.importSource("instagram.com/p/abc"));
        assertNull(RecipeController.importSource("not a url at all"));
    }
}
