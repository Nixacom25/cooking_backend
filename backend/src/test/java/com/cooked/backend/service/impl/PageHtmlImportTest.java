package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Import from page HTML captured by the app's WebView (sites like Allrecipes
 * answer server fetches with HTTP 402, so the server never sees the recipe).
 */
class PageHtmlImportTest {

    private final MarkhorAiServiceImpl service =
            new MarkhorAiServiceImpl(null, new ObjectMapper(), null, null, null, null);

    @Test
    void readsSchemaOrgRecipeFromCapturedHtml() {
        String html = """
                <html><head>
                <meta property="og:image" content="https://img.example.com/beef.jpg">
                <script type="application/ld+json">
                [{"@context":"https://schema.org","@type":["Recipe"],
                  "name":"Quick Beef Stir-Fry",
                  "recipeIngredient":["1 pound beef sirloin, cut into 2-inch strips","1 tablespoon vegetable oil","1 cup broccoli florets"],
                  "recipeInstructions":[{"@type":"HowToStep","text":"Heat oil in a large wok."},{"@type":"HowToStep","text":"Cook beef until browned."}]}]
                </script></head><body></body></html>
                """;

        CreateRecipeRequest req = service.extractRecipeFromPage(
                "https://www.allrecipes.com/recipe/228823/quick-beef-stir-fry/", html, "user@example.com");

        assertEquals("Quick Beef Stir-Fry", req.getName());
        assertEquals(3, req.getIngredients().size());
        assertEquals(2, req.getSteps().size());
    }

    @Test
    void siteSearchFallbackAlwaysGivesBrowsableLinks() {
        var links = MarkhorAiServiceImpl.siteSearchLinks("Pizza Margherita");
        assertFalse(links.isEmpty());
        assertTrue(links.get(0).get("url").startsWith("https://"));
        assertTrue(links.get(0).get("url").contains("Pizza+Margherita"));
    }
}
