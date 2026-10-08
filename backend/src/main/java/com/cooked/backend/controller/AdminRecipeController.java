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
import org.springframework.security.core.Authentication;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/recipes")
@RequiredArgsConstructor
@Tag(name = "Admin Recipe", description = "Endpoints for admin recipe management")
@SecurityRequirement(name = "bearerAuth")
public class AdminRecipeController {

    private final RecipeService recipeService;
    private final com.cooked.backend.service.RecipeModerationService moderation;
    private final com.cooked.backend.service.RecipeNutritionService nutrition;

    @Operation(summary = "Get all recipes for admin")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Page<RecipeResponse>> getAllRecipes(
            @RequestParam(required = false) RecipeOrigin origin,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) java.util.UUID cuisineId,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(required = false) Boolean reported,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var filter = new com.cooked.backend.dto.request.AdminRecipeFilter(origin, name, cuisineId, visibility, hasImage, reported);
        if (!filter.basic()) {
            Pageable unsorted = PageRequest.of(com.cooked.backend.util.PaginationUtils.clampPage(page), com.cooked.backend.util.PaginationUtils.clampSize(size));
            return ResponseEntity.ok(recipeService.getAdminRecipes(filter, unsorted));
        }
        Pageable pageable = PageRequest.of(com.cooked.backend.util.PaginationUtils.clampPage(page), com.cooked.backend.util.PaginationUtils.clampSize(size), Sort.by("updatedAt").ascending());
        return ResponseEntity.ok(recipeService.getAdminRecipes(origin, name, pageable));
    }

    @Operation(summary = "Usage of a recipe: views, cookbooks, meal plans, grocery adds, open reports, assignments")
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.cooked.backend.dto.response.RecipeStatsResponse> recipeStats(@PathVariable UUID id) {
        return ResponseEntity.ok(moderation.stats(id));
    }

    @Operation(summary = "Reports on a recipe, newest first")
    @GetMapping("/{id}/flags")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<com.cooked.backend.dto.response.RecipeFlagResponse>> flags(@PathVariable UUID id) {
        return ResponseEntity.ok(moderation.flags(id));
    }

    @Operation(summary = "Report a problem on a recipe")
    @PostMapping("/{id}/flags")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<com.cooked.backend.dto.response.RecipeFlagResponse> flag(@PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.cooked.backend.dto.request.RecipeFlagRequest body, Authentication auth) {
        return ResponseEntity.ok(moderation.flag(id, body, auth.getName()));
    }

    @Operation(summary = "Mark a report as resolved")
    @PutMapping("/flags/{flagId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.cooked.backend.dto.response.RecipeFlagResponse> resolveFlag(@PathVariable UUID flagId, Authentication auth) {
        return ResponseEntity.ok(moderation.resolve(flagId, auth.getName()));
    }

    @Operation(summary = "Merge a duplicate into this recipe (cookbooks, meal plans, grocery items move; duplicate archived)")
    @PostMapping("/{id}/merge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.cooked.backend.dto.response.RecipeMergeResponse> merge(@PathVariable UUID id,
            @RequestParam UUID duplicateId, Authentication auth) {
        return ResponseEntity.ok(moderation.merge(id, duplicateId, auth.getName()));
    }

    @Operation(summary = "Estimate nutrition per serving with the AI and save it")
    @PostMapping("/{id}/nutrition/estimate")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<com.cooked.backend.dto.response.NutritionEstimateResponse> estimateNutrition(@PathVariable UUID id) {
        return ResponseEntity.ok(nutrition.estimate(id));
    }

    @Operation(summary = "Get single recipe details for admin")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RecipeResponse> getRecipeById(@PathVariable UUID id) {
        return ResponseEntity.ok(recipeService.getAdminRecipeById(id));
    }

    @Operation(summary = "Update full recipe details")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RecipeResponse> updateRecipe(
            @PathVariable UUID id,
            @RequestPart("recipe") String recipeJson,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(recipeService.updateAdminRecipe(id, recipeJson, image));
    }

    @Operation(summary = "Bulk import recipes")
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<MessageResponse> bulkImportRecipes(
            @RequestBody java.util.List<com.cooked.backend.dto.request.CreateRecipeRequest> requests,
            org.springframework.security.core.Authentication auth) {
        recipeService.bulkCreateAdminRecipes(auth.getName(), requests);
        return ResponseEntity.ok(new MessageResponse(requests.size() + " recipes imported successfully"));
    }

    @Operation(summary = "Delete recipe by ID")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<MessageResponse> deleteRecipe(@PathVariable UUID id, Authentication auth) {
        String userEmail = auth != null ? auth.getName() : null;
        recipeService.deleteAdminRecipe(id, userEmail);
        return ResponseEntity.ok(new MessageResponse("Recipe deleted successfully"));
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<MessageResponse> restoreRecipe(@PathVariable UUID id, Authentication auth) {
        String userEmail = auth != null ? auth.getName() : null;
        recipeService.restoreAdminRecipe(id, userEmail);
        return ResponseEntity.ok(new MessageResponse("Recipe restored successfully"));
    }

    @Operation(summary = "Toggle recipe activation status")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RecipeResponse> toggleStatus(
            @PathVariable UUID id,
            @RequestParam(required = false) Boolean status,
            org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(recipeService.toggleRecipeStatus(id, status, auth.getName()));
    }

    @Operation(summary = "Bulk update recipes activation status")
    @PutMapping("/bulk-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<MessageResponse> bulkUpdateStatus(
            @RequestBody java.util.List<UUID> ids,
            @RequestParam Boolean status,
            org.springframework.security.core.Authentication auth) {
        recipeService.bulkUpdateRecipeStatus(ids, status, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Successfully updated activation status for " + ids.size() + " recipes."));
    }
}
