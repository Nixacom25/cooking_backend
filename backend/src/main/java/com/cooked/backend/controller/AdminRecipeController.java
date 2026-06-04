package com.cooked.backend.controller;

import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/recipes")
@RequiredArgsConstructor
@Tag(name = "Admin Recipe", description = "Endpoints for admin recipe management")
@SecurityRequirement(name = "bearerAuth")
public class AdminRecipeController {

    private final RecipeService recipeService;

    @Operation(summary = "Get all recipes for admin")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RecipeResponse>> getAllRecipes(
            @RequestParam(required = false) RecipeOrigin origin,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").ascending());
        return ResponseEntity.ok(recipeService.getAdminRecipes(origin, name, pageable));
    }

    @Operation(summary = "Update full recipe details")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable UUID id,
            @RequestPart("recipe") String recipeJson,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(recipeService.updateAdminRecipe(id, recipeJson, image));
    }

    @Operation(summary = "Delete recipe by ID")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteRecipe(@PathVariable UUID id) {
        recipeService.deleteAdminRecipe(id);
        return ResponseEntity.ok(new MessageResponse("Recipe deleted successfully"));
    }
}
