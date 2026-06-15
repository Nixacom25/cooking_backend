package com.cooked.backend.controller;

import com.cooked.backend.dto.response.IngredientResponse;
import com.cooked.backend.entity.Ingredient;
import com.cooked.backend.repository.IngredientRepository;
import com.cooked.backend.service.CloudinaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ingredients")
@CrossOrigin(origins = "*")
public class IngredientController {

    private final IngredientRepository ingredientRepository;
    private final CloudinaryService cloudinaryService;

    public IngredientController(IngredientRepository ingredientRepository, CloudinaryService cloudinaryService) {
        this.ingredientRepository = ingredientRepository;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping
    public ResponseEntity<List<IngredientResponse>> getAllIngredients() {
        List<IngredientResponse> responses = ingredientRepository.findAll().stream()
                .map(i -> IngredientResponse.builder()
                        .id(i.getId())
                        .name(i.getName())
                        .icon(i.getIcon())
                        .image(i.getImage())
                        .price(i.getPrice())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping(value = "/{id}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<IngredientResponse> updateIngredient(
            @PathVariable UUID id,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) String image,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(required = false) Double price) {
        
        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        if (icon != null) {
            ingredient.setIcon(icon.isBlank() ? null : icon);
        }
        
        if (price != null) {
            ingredient.setPrice(price);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String uploadedImage = cloudinaryService.upload(imageFile);
                ingredient.setImage(uploadedImage);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to upload image", e);
            }
        } else if (image != null) {
            ingredient.setImage(image.isBlank() ? null : image);
        }

        ingredient = ingredientRepository.save(ingredient);

        return ResponseEntity.ok(IngredientResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .icon(ingredient.getIcon())
                .image(ingredient.getImage())
                .price(ingredient.getPrice())
                .build());
    }
}
