package com.cooked.backend.controller;

import com.cooked.backend.entity.RecipeData;
import com.cooked.backend.service.RecipeDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/recipe-data")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecipeDataController {

    private final RecipeDataService recipeDataService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecipeData> create(
            @RequestParam("name") String name,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(recipeDataService.create(name, image));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<RecipeData>> bulkCreate(
            @RequestParam("names") List<String> names,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.ok(recipeDataService.bulkCreate(names, images));
    }

    @GetMapping
    public ResponseEntity<List<RecipeData>> getAll() {
        return ResponseEntity.ok(recipeDataService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeData> getById(@PathVariable Long id) {
        return ResponseEntity.ok(recipeDataService.getById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecipeData> update(
            @PathVariable Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(recipeDataService.update(id, name, image));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipeDataService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteMultiple(@RequestParam("ids") List<Long> ids) {
        recipeDataService.deleteMultiple(ids);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk-update-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<java.util.Map<String, Object>> bulkUpdateImages(@RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.ok(recipeDataService.bulkUpdateImages(files));
    }

    @GetMapping("/pending-images")
    public ResponseEntity<List<com.cooked.backend.entity.Recipe>> getPendingImages() {
        return ResponseEntity.ok(recipeDataService.getRecipesMissingImages());
    }

    @GetMapping("/sync-status")
    public ResponseEntity<List<com.cooked.backend.entity.Recipe>> getSyncStatus() {
        return ResponseEntity.ok(recipeDataService.getRecipesExistingInData());
    }
}
