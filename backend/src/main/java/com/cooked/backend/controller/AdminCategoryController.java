package com.cooked.backend.controller;

import com.cooked.backend.entity.CategoryType;
import com.cooked.backend.entity.RecipeCategory;
import com.cooked.backend.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@Tag(name = "Admin Category", description = "Admin Category management endpoints")
public class AdminCategoryController {

    private final RecipeService recipeService;

    @Operation(summary = "Get all categories or cuisines")
    @GetMapping
    public ResponseEntity<List<com.cooked.backend.dto.response.AdminCategoryResponse>> getAllCategories(
            @RequestParam(required = false) CategoryType type) {
        return ResponseEntity.ok(recipeService.getAllCategories(type));
    }

    @Operation(summary = "Create a new category or cuisine")
    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecipeCategory> createCategory(
            @RequestParam String name,
            @RequestParam(required = false) String image,
            @RequestPart(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
            @RequestParam CategoryType type,
            @RequestParam(required = false, defaultValue = "true") Boolean active) {
        return ResponseEntity.ok(recipeService.createCategory(name, image, imageFile, type, active));
    }

    @Operation(summary = "Update an existing category or cuisine")
    @PutMapping(value = "/{id}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecipeCategory> updateCategory(
            @PathVariable UUID id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String image,
            @RequestPart(value = "imageFile", required = false) org.springframework.web.multipart.MultipartFile imageFile,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(recipeService.updateCategory(id, name, image, imageFile, type, active));
    }

    @Operation(summary = "Delete a category or cuisine")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        recipeService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
