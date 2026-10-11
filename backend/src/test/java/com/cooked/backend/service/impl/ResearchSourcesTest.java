package com.cooked.backend.service.impl;

import com.cooked.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ResearchSourcesTest {

    @Test
    void internalAddressesAreRefused() {
        for (String u : new String[]{"http://127.0.0.1", "http://localhost:8080", "http://10.0.0.5", "http://192.168.1.1", "http://169.254.169.254", "ftp://example.com"}) {
            assertThrows(IOException.class, () -> CompetitorCrawler.assertPublic(URI.create(u)), u);
        }
    }

    @Test
    void urlsAreNormalisedToTheirOrigin() {
        assertEquals("https://www.allrecipes.com", ResearchSourcesServiceImpl.normalise(" www.AllRecipes.com/recipes/123 "));
        assertEquals("http://tasty.co", ResearchSourcesServiceImpl.normalise("http://tasty.co/"));
        assertThrows(BadRequestException.class, () -> ResearchSourcesServiceImpl.normalise("not a site"));
    }

    @Test
    void recipeUrlsAndTitles() {
        assertTrue(CompetitorCrawler.looksLikeRecipe("https://www.allrecipes.com/recipe/228823/quick-beef-stir-fry/"));
        assertFalse(CompetitorCrawler.looksLikeRecipe("https://tasty.co/article/best-knives"));
        assertEquals("Quick beef stir fry", CompetitorCrawler.title("https://www.allrecipes.com/recipe/228823/quick-beef-stir-fry/"));
        assertEquals("Creamy pasta", CompetitorCrawler.title("https://site.com/recipes/creamy-pasta-12345.html"));
    }
}
