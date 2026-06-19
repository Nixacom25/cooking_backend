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

import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.entity.User;

@RestController
@RequestMapping("/api/ingredients")
@CrossOrigin(origins = "*")
public class IngredientController {

    private final IngredientRepository ingredientRepository;
    private final CloudinaryService cloudinaryService;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public IngredientController(IngredientRepository ingredientRepository, CloudinaryService cloudinaryService,
                                UserRepository userRepository, ActivityLogService activityLogService) {
        this.ingredientRepository = ingredientRepository;
        this.cloudinaryService = cloudinaryService;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
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

        java.util.List<String> changedFields = new java.util.ArrayList<>();

        if (icon != null && !icon.equals(ingredient.getIcon())) {
            changedFields.add("l'icône");
            ingredient.setIcon(icon.isBlank() ? null : icon);
        }
        
        if (price != null && !price.equals(ingredient.getPrice())) {
            changedFields.add("le prix");
            ingredient.setPrice(price);
        }

        boolean imageChanged = false;
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String uploadedImage = cloudinaryService.upload(imageFile);
                if (!uploadedImage.equals(ingredient.getImage())) {
                    imageChanged = true;
                }
                ingredient.setImage(uploadedImage);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to upload image", e);
            }
        } else if (image != null && !image.equals(ingredient.getImage())) {
            ingredient.setImage(image.isBlank() ? null : image);
            imageChanged = true;
        }

        if (imageChanged) {
            changedFields.add("l'image");
        }

        final String finalIngredientName = ingredient.getName();

        if (!changedFields.isEmpty()) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                userRepository.findByEmail(auth.getName()).ifPresent(user -> {
                    if (user.getRole() == com.cooked.backend.entity.Role.EDITOR) {
                        activityLogService.logDetailedEditorActivity(user, changedFields, "ingrédient", finalIngredientName, null);
                    }
                });
            }
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
