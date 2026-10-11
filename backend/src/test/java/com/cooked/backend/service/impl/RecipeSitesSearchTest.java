package com.cooked.backend.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSitesSearchTest {

    @Test
    void keepsMatchingRecipePagesOnly() {
        Document doc = Jsoup.parse("""
                <a href="/recipes/chicken-sweetcorn-tacos">App only Chicken tacos</a>
                <a href="/recipes/collection/taco-recipes">Taco collection</a>
                <a href="/recipes/chicken-jalfrezi">Chicken jalfrezi</a>
                <a href="/recipes/beef-stew">Beef stew</a>
                <a href="/about">Chicken tacos</a>""", "https://www.bbcgoodfood.com/search?q=chicken+tacos");
        var site = MarkhorAiServiceImpl.RECIPE_SITES.get(0);
        List<Map<String, String>> r = MarkhorAiServiceImpl.recipeLinks(doc, site, MarkhorAiServiceImpl.searchWords("Chicken Tacos recipe"));
        assertEquals(2, r.size());
        assertEquals("Chicken tacos", r.get(0).get("title"));
        assertEquals("https://www.bbcgoodfood.com/recipes/chicken-sweetcorn-tacos", r.get(0).get("url"));
        assertEquals("Recipe on BBC Good Food", r.get(0).get("snippet"));
    }

    @Test
    void wordsAndSlugTitles() {
        assertEquals(List.of("poulet", "taco"), MarkhorAiServiceImpl.searchWords("Recette poulet tacos"));
        assertEquals("Easy chicken tacos", MarkhorAiServiceImpl.slugTitle("https://www.delish.com/cooking/recipe-ideas/a58716/easy-chicken-tacos-recipe/"));
        assertEquals(1, MarkhorAiServiceImpl.matches("Pâtes à la crème", MarkhorAiServiceImpl.searchWords("pates")));
    }

    @Test
    void blockedSourcesArePaused() {
        MarkhorAiServiceImpl.SearchPause.clear();
        assertFalse(MarkhorAiServiceImpl.SearchPause.isPaused("Google"));
        MarkhorAiServiceImpl.SearchPause.pause("Google", "captcha", org.slf4j.LoggerFactory.getLogger("test"));
        assertTrue(MarkhorAiServiceImpl.SearchPause.isPaused("Google"));
        MarkhorAiServiceImpl.SearchPause.clear();
    }
}
