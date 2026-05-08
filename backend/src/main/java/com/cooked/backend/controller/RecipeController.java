package com.cooked.backend.controller;

import com.cooked.backend.dto.request.AiRecipeGenerationRequest;
import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.AiIngredientDetectionResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.service.AiService;
import com.cooked.backend.service.RecipeService;
import com.cooked.backend.service.TrendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
@Tag(name = "Recipe", description = "Endpoints for managing personal Recipes")
@SecurityRequirement(name = "bearerAuth")
public class RecipeController {

    private final RecipeService recipeService;
    private final AiService aiService;
    private final TrendingService trendingService;

    @Operation(summary = "Import Recipe via Link (AI)")
    @PostMapping("/import")
    public ResponseEntity<CreateRecipeRequest> importRecipeViaLink(@RequestBody Map<String, String> payload,
            Authentication auth) {
        String url = payload.get("url");
        if (url == null || url.isBlank()) {
            throw new com.cooked.backend.exception.BadRequestException("URL is required");
        }
        return ResponseEntity.ok(aiService.extractRecipeFromLink(url, auth.getName()));
    }

    @Operation(summary = "Detect Ingredients from Image (AI)")
    @PostMapping(value = "/detect-ingredients", consumes = "multipart/form-data")
    public ResponseEntity<AiIngredientDetectionResponse> detectIngredients(@RequestParam("file") MultipartFile file,
            Authentication auth) {
        if (file.isEmpty()) {
            throw new com.cooked.backend.exception.BadRequestException("Image file is required");
        }
        return ResponseEntity.ok(aiService.detectIngredients(file, auth.getName()));
    }

    @Operation(summary = "Generate Recipes from Ingredients (AI)")
    @PostMapping("/generate-ai-recipes")
    public ResponseEntity<List<CreateRecipeRequest>> generateAiRecipes(
            @Valid @RequestBody AiRecipeGenerationRequest request,
            Authentication auth) {
        return ResponseEntity.ok(aiService.generateRecipes(request, auth.getName()));
    }

    @Operation(summary = "Scan Recipe via Image (Live)")
    @PostMapping(value = "/scan", consumes = "multipart/form-data")
    public ResponseEntity<com.cooked.backend.dto.response.ScanResponse> scanRecipeViaImage(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        return ResponseEntity.ok(aiService.scan(file, auth.getName()));
    }

    @Operation(summary = "Scan Typed Ingredients (AI)")
    @PostMapping("/scan-typed")
    public ResponseEntity<com.cooked.backend.dto.response.ScanResponse> scanTypedIngredients(
            @RequestBody Map<String, List<String>> payload,
            Authentication auth) {
        List<String> ingredients = payload.get("ingredients");
        if (ingredients == null || ingredients.isEmpty()) {
            throw new com.cooked.backend.exception.BadRequestException("Ingredients list is required");
        }
        return ResponseEntity.ok(aiService.scanTyped(ingredients, auth.getName()));
    }

    @Operation(summary = "Create a new recipe")
    @PostMapping
    public ResponseEntity<RecipeResponse> create(Authentication auth, @Valid @RequestBody CreateRecipeRequest request) {
        return ResponseEntity.ok(recipeService.create(auth.getName(), request));
    }

    @Operation(summary = "Get all my Recipes")
    @GetMapping
    public ResponseEntity<List<RecipeResponse>> getMyRecipes(Authentication auth) {
        return ResponseEntity.ok(recipeService.getMyRecipes(auth.getName()));
    }

    @Operation(summary = "Get a specific Recipe")
    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> getRecipe(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(recipeService.getRecipe(id, auth.getName()));
    }

    @Operation(summary = "Delete a Recipe")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(recipeService.delete(id, auth.getName()));
    }

    @Operation(summary = "Explore public recipes")
    @GetMapping("/explore")
    public ResponseEntity<org.springframework.data.domain.Page<RecipeResponse>> getExploreRecipes(
            @RequestParam(required = false) String cuisine,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(recipeService.getExploreRecipes(cuisine, category, pageable));
    }

    @Operation(summary = "Get all distinct explore cuisines")
    @GetMapping("/explore/cuisines")
    public ResponseEntity<Map<String, Long>> getExploreCuisines() {
        return ResponseEntity.ok(recipeService.getExploreCuisines());
    }

    @Operation(summary = "Get all distinct explore categories")
    @GetMapping("/explore/categories")
    public ResponseEntity<Map<String, Long>> getExploreCategories() {
        return ResponseEntity.ok(recipeService.getExploreCategories());
    }

    @Operation(summary = "Toggle recipe visibility")
    @PutMapping("/{id}/visibility")
    public ResponseEntity<MessageResponse> toggleVisibility(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(recipeService.togglePublicVisibility(id, auth.getName()));
    }

    @Operation(summary = "Toggle recipe favorite status")
    @PutMapping("/{id}/favorite")
    public ResponseEntity<MessageResponse> toggleFavorite(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(recipeService.toggleFavorite(id, auth.getName()));
    }

    @Operation(summary = "Get user's favorite recipes")
    @GetMapping("/favorites")
    public ResponseEntity<org.springframework.data.domain.Page<RecipeResponse>> getFavorites(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(recipeService.getFavoriteRecipes(auth.getName(), pageable));
    }

    @Operation(summary = "Get top creators based on recipe usage")
    @GetMapping("/top-creators")
    public ResponseEntity<org.springframework.data.domain.Page<com.cooked.backend.dto.response.CreatorResponse>> getTopCreators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(recipeService.getTopCreators(pageable));
    }

    @Operation(summary = "Get popular recipes based on usage")
    @GetMapping("/popular")
    public ResponseEntity<org.springframework.data.domain.Page<RecipeResponse>> getPopularRecipes(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        String email = userDetails != null ? userDetails.getUsername() : null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(recipeService.getPopularRecipes(category, email, pageable));
    }

    @Operation(summary = "Search recipes on the web")
    @GetMapping("/web-search")
    public ResponseEntity<List<Map<String, String>>> searchWeb(@RequestParam String query, Authentication auth) {
        return ResponseEntity.ok(aiService.searchWeb(query, auth.getName()));
    }

    @Operation(summary = "Get daily AI trending dishes")
    @GetMapping("/trending-ai")
    public ResponseEntity<List<String>> getTrendingAiDishes() {
        return ResponseEntity.ok(trendingService.getTrendingDishes());
    }

    @Operation(summary = "Get user's recent imports")
    @GetMapping("/imports")
    public ResponseEntity<org.springframework.data.domain.Page<RecipeResponse>> getRecentImports(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(recipeService.getRecentImports(auth.getName(), pageable));
    }

    @Operation(summary = "Validate a suggested recipe")
    @PutMapping("/{id}/validate")
    public ResponseEntity<RecipeResponse> validate(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(recipeService.validateSuggestedRecipe(id, auth.getName()));
    }

    @Operation(summary = "Get sharing link and mark as public")
    @GetMapping("/{id}/share")
    public ResponseEntity<Map<String, String>> getShareLink(@PathVariable UUID id, Authentication auth) {
        String link = recipeService.getShareLink(id, auth.getName());
        return ResponseEntity.ok(Map.of("link", link));
    }
}
