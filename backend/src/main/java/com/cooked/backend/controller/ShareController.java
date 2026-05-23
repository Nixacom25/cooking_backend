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
 * ShareController — serves Open Graph–enriched HTML pages for recipe share links.
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
    private static final String STORE_URL = "https://cookedapp.app";

    /** Public site name shown in the social preview */
    private static final String SITE_NAME = "Cooked";

    /** Site domain shown under the title in the preview */
    private static final String SITE_DOMAIN = "cookedapp.app";

    /**
     * GET /share/recipes/{id}
     *
     * Returns an HTML page whose <head> contains all Open Graph and Twitter Card
     * meta tags required for a rich link preview.  A small inline script then
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
        String recipeName  = escapeHtml(recipe.getName());
        String recipeImage = (recipe.getImage() != null && !recipe.getImage().isBlank())
                ? escapeHtml(recipe.getImage())
                : "https://api.cookedapp.com/og-default.jpg";   // fallback OG image

        String cuisine  = recipe.getCuisine()  != null ? recipe.getCuisine().getName()  : "";
        String category = recipe.getCategory() != null ? recipe.getCategory().getName() : "";

        String description = buildDescription(recipe, cuisine, category);
        String deepLink    = APP_SCHEME + id;
        String shareUrl    = "https://api.cookedapp.com/share/recipes/" + id;

        String html = buildHtml(recipeName, recipeImage, description, deepLink, shareUrl);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildDescription(Recipe recipe, String cuisine, String category) {
        StringBuilder sb = new StringBuilder();
        if (!cuisine.isBlank())  sb.append(cuisine).append(" · ");
        if (!category.isBlank()) sb.append(category).append(" · ");
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
            "  <meta property=\"og:type\"        content=\"article\" />\n" +
            "  <meta property=\"og:site_name\"   content=\"" + SITE_NAME + "\" />\n" +
            "  <meta property=\"og:title\"       content=\"" + name + "\" />\n" +
            "  <meta property=\"og:description\" content=\"" + escapeHtml(description) + "\" />\n" +
            "  <meta property=\"og:image\"       content=\"" + image + "\" />\n" +
            "  <meta property=\"og:image:width\"  content=\"1200\" />\n" +
            "  <meta property=\"og:image:height\" content=\"630\" />\n" +
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
            "    * { margin:0; padding:0; box-sizing:border-box; }\n" +
            "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;\n" +
            "           background:#0f0f0f; color:#fff; min-height:100vh;\n" +
            "           display:flex; align-items:center; justify-content:center; }\n" +
            "    .card { max-width:420px; width:100%; background:#1a1a1a; border-radius:20px;\n" +
            "            overflow:hidden; box-shadow:0 20px 60px rgba(0,0,0,.6); }\n" +
            "    .card img { width:100%; aspect-ratio:4/3; object-fit:cover; display:block; }\n" +
            "    .card-body { padding:20px; }\n" +
            "    .card-body h1 { font-size:1.3rem; font-weight:700; margin-bottom:6px; }\n" +
            "    .card-body p  { font-size:.85rem; color:#aaa; margin-bottom:18px; }\n" +
            "    .card-body .domain { font-size:.75rem; color:#666; }\n" +
            "    .btn { display:block; width:100%; padding:14px;\n" +
            "           background:linear-gradient(135deg,#ff6b35,#f7c59f);\n" +
            "           color:#fff; font-size:1rem; font-weight:700;\n" +
            "           border:none; border-radius:12px; cursor:pointer;\n" +
            "           text-decoration:none; text-align:center; margin-bottom:10px; }\n" +
            "    .btn-secondary { background:#2a2a2a; color:#ccc; }\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "  <div class=\"card\">\n" +
            "    <img src=\"" + image + "\" alt=\"" + name + "\" />\n" +
            "    <div class=\"card-body\">\n" +
            "      <h1>" + name + "</h1>\n" +
            "      <p>" + escapeHtml(description) + "</p>\n" +
            "      <span class=\"domain\">" + SITE_DOMAIN + "</span>\n" +
            "      <br/><br/>\n" +
            "      <a id=\"openBtn\" class=\"btn\" href=\"" + deepLink + "\">🍳 Open in Cooked</a>\n" +
            "      <a class=\"btn btn-secondary\" href=\"" + STORE_URL + "\">Download Cooked</a>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "\n" +
            "  <script>\n" +
            "    // Attempt deep-link; if app isn't installed, fallback lands on store page\n" +
            "    var deepLink = '" + deepLink + "';\n" +
            "    var storeUrl = '" + STORE_URL + "';\n" +
            "    var btn = document.getElementById('openBtn');\n" +
            "    btn.addEventListener('click', function(e) {\n" +
            "      e.preventDefault();\n" +
            "      var start = Date.now();\n" +
            "      setTimeout(function() {\n" +
            "        if (Date.now() - start < 2000) { window.location.href = storeUrl; }\n" +
            "      }, 1500);\n" +
            "      window.location.href = deepLink;\n" +
            "    });\n" +
            "  </script>\n" +
            "</body>\n" +
            "</html>\n";
    }

    private String buildFallbackHtml() {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"/>" +
            "<title>Recipe not found — " + SITE_NAME + "</title>" +
            "<meta http-equiv=\"refresh\" content=\"2;url=" + STORE_URL + "\" />" +
            "</head><body style=\"font-family:sans-serif;text-align:center;padding:40px;background:#0f0f0f;color:#fff\">" +
            "<p>This recipe is no longer available.</p>" +
            "<a href=\"" + STORE_URL + "\" style=\"color:#ff6b35\">Go to Cooked</a></body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
