package com.cooked.backend.controller;

import com.cooked.backend.entity.Recipe;
import com.cooked.backend.repository.RecipeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    /** App deep-link scheme — update if the scheme changes */
    private static final String APP_SCHEME = "cooked://recipe/";

    /** Fallback URL when the app is not installed */
    private static final String STORE_URL = "https://link.cookedapp.com";

    /** Public site name shown in the social preview */
    private static final String SITE_NAME = "Cooked";

    /** Site domain shown under the title in the preview */
    private static final String SITE_DOMAIN = "link.cookedapp.com";

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
            String fallback = buildFallbackHtml();
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(fallback);
        }

        // ── Build meta values ────────────────────────────────────────────────
        String recipeName = escapeHtml(recipe.getName());
        String rawImage = (recipe.getImage() != null && !recipe.getImage().isBlank())
                ? recipe.getImage()
                : "https://link.cookedapp.com/og-default.jpg";

        // WhatsApp requires images < 300KB. Optimize Cloudinary URLs for Open Graph
        // (1200x630, high compression, JPEG)
        if (rawImage.contains("res.cloudinary.com") && rawImage.contains("/image/upload/")) {
            rawImage = rawImage.replaceFirst("/image/upload/", "/image/upload/w_1200,h_630,c_fill,q_auto,f_jpg/");
        }
        String recipeImage = escapeHtml(rawImage);

        String cuisine = recipe.getCuisine() != null ? recipe.getCuisine().getName() : "";
        String category = recipe.getCategory() != null ? recipe.getCategory().getName() : "";

        String description = buildDescription(recipe, cuisine, category);
        String deepLink = APP_SCHEME + id;
        String shareUrl = "https://link.cookedapp.com/share/recipes/" + id;

        String html = buildHtml(recipeName, recipeImage, description, deepLink, shareUrl);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    private String buildFallbackHtml() {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/>" +
                "<title>Recipe not found — " + SITE_NAME + "</title>" +
                "<meta http-equiv=\"refresh\" content=\"2;url=" + STORE_URL + "\" />" +
                "</head><body style=\"font-family:sans-serif;text-align:center;padding:40px;background:#0f0f0f;color:#fff\">"
                +
                "<p>This recipe is no longer available.</p>" +
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
