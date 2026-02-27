package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
@Tag(name = "Recipe", description = "Endpoints for managing personal Recipes")
@SecurityRequirement(name = "bearerAuth")
public class RecipeController {

    private final RecipeService recipeService;

    @Operation(summary = "Create a new Recipe (with dynamic Ingredients)")
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
}
