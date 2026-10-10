package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.cooked.backend.entity.Recipe;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.RecipeNutritionService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Import from page HTML captured by the app's WebView (sites like Allrecipes
 * answer server fetches with HTTP 402, so the server never sees the recipe).
 */
class PageHtmlImportTest {

    private final RecipeRepository recipeRepository = mock(RecipeRepository.class);
    private final RecipeNutritionService nutrition = mock(RecipeNutritionService.class);
    private final MarkhorAiServiceImpl service =
            new MarkhorAiServiceImpl(null, new ObjectMapper(), null, null, recipeRepository, null, nutrition);

    private static final String STEPS = "\"recipeInstructions\":[{\"@type\":\"HowToStep\",\"text\":\"Mix everything well.\"}]";

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

    @Test
    void readsJsonLdImageArrayAndCaloriesFromThePage() {
        String html = """
                <html><head><script type="application/ld+json">
                {"@type":"Recipe","name":"Chicken Tacos",
                 "image":[{"@type":"ImageObject","url":"/photos/tacos.jpg"}],
                 "nutrition":{"@type":"NutritionInformation","calories":"420 kcal"},
                 "recipeIngredient":["2 chicken breasts","8 tortillas"],
                """ + STEPS + "}</script></head><body></body></html>";

        CreateRecipeRequest req = service.extractRecipeFromPage("https://site.example.com/tacos", html, "u@example.com");

        assertEquals("https://site.example.com/photos/tacos.jpg", req.getImage());
        assertEquals(420, req.getKcal());
    }

    @Test
    void pageWithoutPhotoBorrowsClosestRecipeImageFromOurBase() {
        Recipe twin = new Recipe();
        twin.setName("Chicken Tacos Supreme");
        twin.setImage("https://res.cloudinary.com/cooked/tacos.jpg");
        when(recipeRepository.findExploreRecipesByKeywords(any(), any(), any(), any(), any())).thenReturn(List.of(twin));

        String html = """
                <html><head><script type="application/ld+json">
                {"@type":"Recipe","name":"Chicken Tacos","recipeIngredient":["2 chicken breasts","8 tortillas"],
                """ + STEPS + "}</script></head><body></body></html>";

        CreateRecipeRequest req = service.extractRecipeFromPage("https://site.example.com/tacos", html, "u@example.com");

        assertEquals("https://res.cloudinary.com/cooked/tacos.jpg", req.getImage());
        // kcal missing on the page -> estimated, never left at a made-up value
        assertNull(req.getKcal());
        verify(nutrition).fillMissingKcal(anyList());
    }

    @Test
    void noMatchAnywhereStillGivesAnImage() {
        CreateRecipeRequest req = new CreateRecipeRequest();
        req.setName("Zzyzx");
        when(recipeRepository.findRandomPopularRecipes(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.finishImport(req);

        assertNotNull(req.getImage());
        assertFalse(req.getImage().isBlank());
    }

    @Test
    void youtubeLinksUseTheVideoThumbnail() {
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                MarkhorAiServiceImpl.youtubeThumbnail("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=3"));
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                MarkhorAiServiceImpl.youtubeThumbnail("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                MarkhorAiServiceImpl.youtubeThumbnail("https://youtube.com/shorts/dQw4w9WgXcQ"));
        assertNull(MarkhorAiServiceImpl.youtubeThumbnail("https://www.allrecipes.com/x"));
    }

    @Test
    void parsesCaloriesText() {
        assertEquals(350, MarkhorAiServiceImpl.parseKcal("350 calories"));
        assertEquals(1200, MarkhorAiServiceImpl.parseKcal("1,200 kcal"));
        assertNull(MarkhorAiServiceImpl.parseKcal("0"));
        assertNull(MarkhorAiServiceImpl.parseKcal(null));
    }
}
