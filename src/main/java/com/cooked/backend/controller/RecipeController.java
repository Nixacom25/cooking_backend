package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.service.OpenAiService;
import com.cooked.backend.service.RecipeService;
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
    private final OpenAiService openAiService;

    @Operation(summary = "Import Recipe via Link (AI)")
    @PostMapping("/import")
    public ResponseEntity<CreateRecipeRequest> importRecipeViaLink(@RequestBody Map<String, String> payload,
            Authentication auth) {
        String url = payload.get("url");
        if (url == null || url.isBlank()) {
            throw new com.cooked.backend.exception.BadRequestException("URL is required");
        }
        return ResponseEntity.ok(openAiService.extractRecipeFromLink(url, auth.getName()));
    }

    @Operation(summary = "Scan Recipe via Image (AI)")
    @PostMapping(value = "/scan", consumes = "multipart/form-data")
    public ResponseEntity<CreateRecipeRequest> scanRecipeViaImage(@RequestParam("file") MultipartFile file,
            Authentication auth) {
        if (file.isEmpty()) {
            throw new com.cooked.backend.exception.BadRequestException("Image file is required");
        }
        return ResponseEntity.ok(openAiService.extractRecipeFromImage(file, auth.getName()));
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(recipeService.getExploreRecipes(pageable));
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
}
