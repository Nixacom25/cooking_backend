package com.cooked.backend.controller;

import com.cooked.backend.entity.RecipeData;
import com.cooked.backend.service.RecipeDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipe-data")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecipeDataController {

    private final RecipeDataService recipeDataService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecipeData> create(
            @RequestParam("name") String name,
            @RequestParam(value = "image", required = false) org.springframework.web.multipart.MultipartFile image) {
        return ResponseEntity.ok(recipeDataService.create(name, image));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<java.util.List<RecipeData>> bulkCreate(
            @RequestParam("names") java.util.List<String> names,
            @RequestParam(value = "images", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> images) {
        return ResponseEntity.ok(recipeDataService.bulkCreate(names, images));
    }

    @GetMapping
    public ResponseEntity<java.util.List<RecipeData>> getAll() {
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
            @RequestParam(value = "image", required = false) org.springframework.web.multipart.MultipartFile image) {
        return ResponseEntity.ok(recipeDataService.update(id, name, image));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipeDataService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Void> deleteMultiple(@RequestParam("ids") java.util.List<Long> ids) {
        recipeDataService.deleteMultiple(ids);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk-update-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<java.util.Map<String, Object>> bulkUpdateImages(@RequestParam("files") java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        return ResponseEntity.ok(recipeDataService.bulkUpdateImages(files));
    }

    @GetMapping("/pending-images")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getPendingImages() {
        return ResponseEntity.ok(recipeDataService.getRecipesMissingImages());
    }

    @GetMapping("/sync-status")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getSyncStatus() {
        return ResponseEntity.ok(recipeDataService.getRecipesExistingInData());
    }
}
