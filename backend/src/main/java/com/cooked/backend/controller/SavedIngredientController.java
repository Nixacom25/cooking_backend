package com.cooked.backend.controller;

import com.cooked.backend.dto.response.SavedIngredientResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.UserSavedIngredient;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSavedIngredientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ingredients")
@RequiredArgsConstructor
@Tag(name = "Ingredient", description = "Endpoints for managing user saved ingredients")
@SecurityRequirement(name = "bearerAuth")
public class SavedIngredientController {

    private final UserSavedIngredientRepository savedIngredientRepository;
    private final UserRepository userRepository;
    private final com.cooked.backend.repository.IngredientRepository ingredientRepository;
    private final com.cooked.backend.repository.RecipeIngredientRepository recipeIngredientRepository;

    @Operation(summary = "Get recently used ingredients")
    @GetMapping("/recent")
    public ResponseEntity<List<com.cooked.backend.entity.Ingredient>> getRecentIngredients(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new BadRequestException("User not found"));
        return ResponseEntity.ok(recipeIngredientRepository.findRecentIngredientsByUserId(user.getId(), org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    @Operation(summary = "Search for ingredients")
    @GetMapping("/search")
    public ResponseEntity<List<com.cooked.backend.entity.Ingredient>> searchIngredients(@RequestParam String q) {
        if (q == null || q.length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(ingredientRepository.findByNameContainingIgnoreCase(q));
    }

    @Operation(summary = "Get all my saved ingredients")
    @GetMapping("/saved")
    public ResponseEntity<List<SavedIngredientResponse>> getMySavedIngredients(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new BadRequestException("User not found"));
        
        List<SavedIngredientResponse> responses = savedIngredientRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(i -> SavedIngredientResponse.builder()
                        .id(i.getId())
                        .name(i.getName())
                        .icon(i.getIcon())
                        .createdAt(i.getCreatedAt())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Save an ingredient")
    @PostMapping("/saved")
    public ResponseEntity<SavedIngredientResponse> saveIngredient(@RequestBody Map<String, String> payload, Authentication auth) {
        String name = payload.get("name");
        String icon = payload.getOrDefault("icon", "🥕");

        if (name == null || name.isBlank()) {
            throw new BadRequestException("Ingredient name is required");
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Check if already exists for this user to avoid duplication (or just return existing)
        java.util.List<UserSavedIngredient> all = savedIngredientRepository.findAllByUserOrderByCreatedAtDesc(user);
        java.util.Optional<UserSavedIngredient> existing = all.stream()
                .filter(i -> i.getName().equalsIgnoreCase(name))
                .findFirst();

        UserSavedIngredient savedIngredient;
        if (existing.isPresent()) {
            savedIngredient = existing.get();
        } else {
            savedIngredient = UserSavedIngredient.builder()
                .user(user)
                .name(name)
                .icon(icon)
                .build();
            savedIngredient = savedIngredientRepository.save(savedIngredient);
        }

        return ResponseEntity.ok(SavedIngredientResponse.builder()
                .id(savedIngredient.getId())
                .name(savedIngredient.getName())
                .icon(savedIngredient.getIcon())
                .createdAt(savedIngredient.getCreatedAt())
                .build());
    }

    @Operation(summary = "Unsave an ingredient (by ID)")
    @DeleteMapping("/saved/{id}")
    public ResponseEntity<Void> unsaveIngredient(@PathVariable UUID id, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new BadRequestException("User not found"));
        
        UserSavedIngredient item = savedIngredientRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Ingredient not found"));
        
        if (!item.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Access denied");
        }

        savedIngredientRepository.delete(item);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unsave an ingredient (by Name)")
    @DeleteMapping("/saved/name/{name}")
    @Transactional
    public ResponseEntity<Void> unsaveIngredientByName(@PathVariable String name, Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new BadRequestException("User not found"));
        
        savedIngredientRepository.deleteByUserAndName(user, name);
        return ResponseEntity.noContent().build();
    }
}
