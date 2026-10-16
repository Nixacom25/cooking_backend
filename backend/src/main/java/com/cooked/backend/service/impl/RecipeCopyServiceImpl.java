package com.cooked.backend.service.impl;

import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeIngredient;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.RecipeCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeCopyServiceImpl implements RecipeCopyService {

    private final RecipeRepository recipeRepository;

    @Override
    @Transactional
    public Recipe copyForUser(Recipe original, User user) {
        if (original.getUser() != null && original.getUser().getId().equals(user.getId())) {
            return original;
        }

        // Same name = same recipe for this user: avoids piling up clones.
        Optional<Recipe> existing = recipeRepository.findByUserIdAndName(user.getId(), original.getName());
        if (existing.isPresent()) {
            Recipe mine = existing.get();
            if (mine.isDeleted()) {
                // Saving it again restores the copy.
                mine.setDeleted(false);
                mine = recipeRepository.save(mine);
            }
            return mine;
        }

        return clone(original, user);
    }

    private Recipe clone(Recipe original, User user) {
        Recipe clone = Recipe.builder()
                .user(user)
                .name(original.getName())
                .image(original.getImage())
                .cookTime(original.getCookTime())
                .prepTime(original.getPrepTime())
                .kcal(original.getKcal())
                .categories(original.getCategories() != null ? new HashSet<>(original.getCategories()) : new HashSet<>())
                .cuisine(original.getCuisine())
                .servings(original.getServings())
                .tips(original.getTips())
                .steps(original.getSteps() != null ? new ArrayList<>(original.getSteps()) : new ArrayList<>())
                .equipment(original.getEquipment() != null ? new ArrayList<>(original.getEquipment()) : new ArrayList<>())
                .isPublic(false) // Copies are private to the user
                // Not scanned by this user: must never count towards savings.
                .origin(RecipeOrigin.EXPLORE)
                .sourceUrl(original.getSourceUrl())
                .build();

        Recipe savedClone = recipeRepository.save(clone);

        if (original.getRecipeIngredients() != null) {
            Set<RecipeIngredient> clonedIngredients = original.getRecipeIngredients().stream()
                    .map(ri -> RecipeIngredient.builder()
                            .recipe(savedClone)
                            .ingredient(ri.getIngredient())
                            .quantity(ri.getQuantity())
                            .build())
                    .collect(Collectors.toSet());
            savedClone.setRecipeIngredients(clonedIngredients);
            return recipeRepository.save(savedClone);
        }

        return savedClone;
    }
}
