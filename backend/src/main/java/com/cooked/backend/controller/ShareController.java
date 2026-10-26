package com.cooked.backend.controller;

import com.cooked.backend.entity.Cookbook;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.CreatorRecipeImageService;
import com.cooked.backend.service.ShareImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * ShareController — serves Open Graph–enriched HTML pages for recipe share
 * links.
 *
 * When a user shares a recipe, the share URL points here.
 * Social crawlers (WhatsApp, iMessage, Telegram, Twitter, etc.) fetch this
 * endpoint and read the <meta property="og:*"> tags to render the rich preview
 * with recipe image, title and site name.
 *
 * Human visitors are instantly redirected to the deep-link or landing page.
 */
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
@Tag(name = "Share", description = "Open Graph share pages for social preview")
public class ShareController {

    private final RecipeRepository recipeRepository;
    private final CookbookRepository cookbookRepository;
    private final ShareImageService shareImageService;
    private final CreatorRecipeImageService creatorRecipeImages;

    /** App deep-link scheme — update if the scheme changes */
    private static final String APP_SCHEME = "cooked://recipe/";
    private static final String COOKBOOK_APP_SCHEME = "cooked://cookbook/";
    private static final String DEFAULT_OG_IMAGE = "https://link.cookedapp.com/og-default.jpg";

    /** Fallback URL when the app is not installed */
    private static final String STORE_URL = "https://link.cookedapp.com";

    /** Public site name shown in the social preview */
    private static final String SITE_NAME = "Cooked";

    /**
     * GET /share/recipes/{id}
     *
     * Returns an HTML page whose <head> contains all Open Graph and Twitter Card
     * meta tags required for a rich link preview. A small inline script then
     * attempts a deep-link redirect; on failure it falls back to the landing page.
     */
    @Operation(summary = "Shared recipe preview page (Open Graph)")
    @GetMapping(value = "/recipes/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> shareRecipe(@PathVariable UUID id) {

        Recipe recipe = recipeRepository.findById(id).orElse(null);

        // ── Fallback when recipe not found ──────────────────────────────────
        if (recipe == null || !recipe.isPublic()) {
            String fallback = buildFallbackHtml("Recipe not found", "This recipe is no longer available.");
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(fallback);
        }

        // ── Build meta values ────────────────────────────────────────────────
        String recipeName = escapeHtml(recipe.getName());
        String recipeImage = escapeHtml(ogImage(recipe.getImage()));

        String cuisine = recipe.getCuisine() != null ? recipe.getCuisine().getName() : "";
        String category = recipe.getCategories() != null && !recipe.getCategories().isEmpty() ? recipe.getCategories().iterator().next().getName() : "";

        String description = buildDescription(recipe, cuisine, category);
        String deepLink = APP_SCHEME + id;
        String shareUrl = "https://link.cookedapp.com/share/recipes/" + id;

        String html = buildHtml(recipeName, recipeImage, description, deepLink, shareUrl);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * GET /share/cookbooks/{id}
     *
     * Same as the recipe page, for a cookbook the owner chose to share. The
     * app opens it on a "receiving cookbook" screen that imports it.
     */
    @Operation(summary = "Shared cookbook preview page (Open Graph)")
    @GetMapping(value = "/cookbooks/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> shareCookbook(@PathVariable UUID id) {
        Cookbook cookbook = cookbookRepository.findById(id).filter(Cookbook::isShared).orElse(null);

        if (cookbook == null) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildFallbackHtml("Cookbook not found", "This cookbook is no longer available."));
        }

        var recipes = activeRecipes(cookbook);
        List<String> images = recipeImages(recipes);
        // Four recipes or more: a 2x2 collage (the version busts crawler
        // caches when the recipes change); otherwise the first recipe image.
        String cover = images.size() >= ShareImageService.COLLAGE_SIZE
                ? "https://link.cookedapp.com/share/cookbooks/" + id + "/cover.jpg?v="
                        + Integer.toHexString(images.subList(0, ShareImageService.COLLAGE_SIZE).hashCode())
                : ogImage(images.isEmpty() ? null : images.get(0));

        String description = recipes.size() + (recipes.size() == 1 ? " recipe" : " recipes")
                + " shared on " + SITE_NAME;
        String html = buildHtml(
                escapeHtml(cookbook.getName()),
                escapeHtml(cover),
                description,
                COOKBOOK_APP_SCHEME + id,
                "https://link.cookedapp.com/share/cookbooks/" + id);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * GET /share/cookbooks/{id}/cover.jpg
     *
     * Open Graph image of a shared cookbook: its first four recipe images in a
     * 2x2 grid. Falls back to the first image when the collage can't be built.
     */
    @Operation(summary = "Shared cookbook preview image (Open Graph)")
    @GetMapping(value = "/cookbooks/{id}/cover.jpg")
    public ResponseEntity<byte[]> shareCookbookCover(@PathVariable UUID id) {
        Cookbook cookbook = cookbookRepository.findById(id).filter(Cookbook::isShared).orElse(null);
        if (cookbook == null) return ResponseEntity.notFound().build();

        List<String> images = recipeImages(activeRecipes(cookbook));
        return shareImageService.collage(images)
                .map(jpeg -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                        .body(jpeg))
                .orElseGet(() -> ResponseEntity.status(302)
                        .header(HttpHeaders.LOCATION, ogImage(images.isEmpty() ? null : images.get(0)))
                        .build());
    }

    /**
     * GET /share/photo-unavailable.jpg
     *
     * "Cooked · Photo unavailable" image given to a creator recipe whose AI photo
     * could not be generated (see CreatorRecipeImageService).
     */
    @Operation(summary = "Placeholder shown when a recipe photo is unavailable")
    @GetMapping(value = "/photo-unavailable.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> photoUnavailable() throws java.io.IOException {
        try (var in = new org.springframework.core.io.ClassPathResource("share/photo-unavailable.jpg").getInputStream()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                    .body(in.readAllBytes());
        }
    }

    /**
     * GET /share/ai-photo/{id}.jpg
     *
     * Link given to a creator recipe whose AI photo wasn't ready when the scan
     * answered: waits for the photo, then redirects to it. If it could not be
     * made, redirects (uncached) to the recipe's previous image.
     */
    @Operation(summary = "Creator recipe AI photo, served once it is generated")
    @GetMapping("/ai-photo/{id}.jpg")
    public ResponseEntity<Void> aiPhoto(@PathVariable UUID id) {
        CreatorRecipeImageService.PhotoLink link = creatorRecipeImages.photoLink(id);
        if (link == null) return ResponseEntity.notFound().build();
        CacheControl cache = link.done()
                ? CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic()
                : CacheControl.noStore();
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, link.url())
                .cacheControl(cache)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Newest first, in a stable order: the recipes are an unordered Set. */
    private List<Recipe> activeRecipes(Cookbook cookbook) {
        return cookbook.getRecipes() == null ? List.of()
                : cookbook.getRecipes().stream()
                        .filter(Objects::nonNull)
                        .filter(r -> !r.isDeleted())
                        .sorted(Comparator.comparing(Recipe::getCreatedAt,
                                        Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                                .thenComparing(Recipe::getId))
                        .toList();
    }

    private List<String> recipeImages(List<Recipe> recipes) {
        return recipes.stream()
                .map(Recipe::getImage)
                .filter(img -> img != null && !img.isBlank())
                .distinct()
                .toList();
    }

    /**
     * WhatsApp requires images < 300KB: Cloudinary URLs are resized for Open
     * Graph (1200x630, high compression, JPEG).
     */
    private String ogImage(String image) {
        String raw = (image != null && !image.isBlank()) ? image : DEFAULT_OG_IMAGE;
        if (raw.contains("res.cloudinary.com") && raw.contains("/image/upload/")) {
            raw = raw.replaceFirst("/image/upload/", "/image/upload/w_1200,h_630,c_fill,q_auto,f_jpg/");
        }
        return raw;
    }

    private String buildDescription(Recipe recipe, String cuisine, String category) {
        StringBuilder sb = new StringBuilder();
        if (!cuisine.isBlank())
            sb.append(cuisine).append(" · ");
        if (!category.isBlank())
            sb.append(category).append(" · ");
        if (recipe.getCookTime() != null && recipe.getCookTime() > 0)
            sb.append(recipe.getCookTime()).append(" min · ");
        if (recipe.getKcal() != null && recipe.getKcal() > 0)
            sb.append(recipe.getKcal()).append(" kcal");
        String result = sb.toString().replaceAll(" · $", "").trim();
        return result.isBlank() ? "Check out this recipe on " + SITE_NAME + "!" : result;
    }

    private String buildHtml(String name, String image, String description,
            String deepLink, String shareUrl) {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\" />\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n" +
                "  <title>" + name + " — " + SITE_NAME + "</title>\n" +
                "\n" +
                "  <!-- ── Open Graph (WhatsApp, Telegram, Facebook, iMessage) ── -->\n" +
                "  <meta property=\"og:type\"        content=\"website\" />\n" +
                "  <meta property=\"og:site_name\"   content=\"" + SITE_NAME + "\" />\n" +
                "  <meta property=\"og:title\"       content=\"" + name + "\" />\n" +
                "  <meta property=\"og:description\" content=\"" + escapeHtml(description) + "\" />\n" +
                "  <meta property=\"og:image\"       content=\"" + image + "\" />\n" +
                "  <meta property=\"og:image:width\"  content=\"1200\" />\n" +
                "  <meta property=\"og:image:height\" content=\"630\" />\n" +
                "  <meta property=\"og:image:type\"   content=\"image/jpeg\" />\n" +
                "  <meta property=\"og:url\"         content=\"" + shareUrl + "\" />\n" +
                "\n" +
                "  <!-- ── Twitter / X Card ── -->\n" +
                "  <meta name=\"twitter:card\"        content=\"summary_large_image\" />\n" +
                "  <meta name=\"twitter:title\"       content=\"" + name + "\" />\n" +
                "  <meta name=\"twitter:description\" content=\"" + escapeHtml(description) + "\" />\n" +
                "  <meta name=\"twitter:image\"       content=\"" + image + "\" />\n" +
                "  <meta name=\"twitter:site\"        content=\"@" + SITE_NAME.toLowerCase() + "\" />\n" +
                "\n" +
                "  <!-- ── iOS Smart App Banner ── -->\n" +
                "  <meta name=\"apple-itunes-app\" content=\"app-id=YOURAPPID\" />\n" +
                "\n" +
                "  <style>\n" +
                "    body { background:#0f0f0f; margin:0; padding:0; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <script>\n" +
                "    // Auto-redirect to deep link or fallback to store\n" +
                "    var deepLink = '" + deepLink + "';\n" +
                "    var storeUrl = '" + STORE_URL + "';\n" +
                "    var start = Date.now();\n" +
                "    setTimeout(function() {\n" +
                "      if (Date.now() - start < 2000) { window.location.href = storeUrl; }\n" +
                "    }, 1500);\n" +
                "    window.location.href = deepLink;\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>\n";
    }

    private String buildFallbackHtml(String title, String message) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/>" +
                "<title>" + title + " — " + SITE_NAME + "</title>" +
                "<meta http-equiv=\"refresh\" content=\"2;url=" + STORE_URL + "\" />" +
                "</head><body style=\"font-family:sans-serif;text-align:center;padding:40px;background:#0f0f0f;color:#fff\">"
                +
                "<p>" + message + "</p>" +
                "<a href=\"" + STORE_URL + "\" style=\"color:#ff6b35\">Go to Cooked</a></body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
