package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeIngredientResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final CookbookRepository cookbookRepository;

    @Override
    @Transactional
    public RecipeResponse create(String userEmail, CreateRecipeRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (recipeRepository.existsByUserIdAndName(user.getId(), request.getName())) {
            throw new BadRequestException("You already have a recipe named '" + request.getName() + "'.");
        }

        Recipe recipe = Recipe.builder()
                .user(user)
                .name(request.getName())
                .image(request.getImage())
                .cookTime(request.getCookTime())
                .kcal(request.getKcal())
                .build();

        // Handle Cookbook Links
        Set<Cookbook> attachedCookbooks = new HashSet<>();
        if (request.getCookbookIds() != null && !request.getCookbookIds().isEmpty()) {
            for (UUID cbId : request.getCookbookIds()) {
                Cookbook cb = cookbookRepository.findById(cbId)
                        .orElseThrow(() -> new ResourceNotFoundException("Cookbook with id " + cbId + " not found"));
                if (!cb.getUser().getId().equals(user.getId())) {
                    throw new BadRequestException("You can only add recipes to your own cookbooks.");
                }
                attachedCookbooks.add(cb);
            }
        }
        recipe.setCookbooks(attachedCookbooks);

        // Pre-save to generate ID for relations
        Recipe savedRecipe = recipeRepository.save(recipe);

        // Handle Ingredients
        Set<RecipeIngredient> recipeIngredients = new HashSet<>();
        if (request.getIngredients() != null) {
            for (IngredientPayload payload : request.getIngredients()) {
                Ingredient ingredient = ingredientRepository.findByName(payload.getName().toLowerCase())
                        .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                                .name(payload.getName().toLowerCase())
                                .icon(payload.getIcon())
                                .build()));

                RecipeIngredient ri = RecipeIngredient.builder()
                        .recipe(savedRecipe)
                        .ingredient(ingredient)
                        .quantity(payload.getQuantity())
                        .build();
                recipeIngredients.add(ri);
            }
        }
        savedRecipe.setRecipeIngredients(recipeIngredients);
        savedRecipe = recipeRepository.save(savedRecipe);

        // Update cookbooks with the new recipe
        for (Cookbook cb : attachedCookbooks) {
            cb.getRecipes().add(savedRecipe);
            cookbookRepository.save(cb);
        }

        return mapToResponse(savedRecipe);
    }

    @Override
    public List<RecipeResponse> getMyRecipes(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return recipeRepository.findAllByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RecipeResponse getRecipe(UUID id, String userEmail) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.getUser().getEmail().equals(userEmail)) {
            // "si un autre cree le meme nom cela doit pas lui affiché erreur"
            // So everyone's recipes are their own. We just restrict viewing to own for now,
            // or public?
            // The prompt says uniqueness is per-client, doesn't explicitly restrict
            // viewing, but generally you only see your own in a dashboard.
            throw new BadRequestException("You do not have permission to view this recipe.");
        }

        return mapToResponse(recipe);
    }

    @Override
    @Transactional
    public MessageResponse delete(UUID id, String userEmail) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to delete this recipe.");
        }

        // Remove recipe from cookbooks first
        for (Cookbook cb : recipe.getCookbooks()) {
            cb.getRecipes().remove(recipe);
            cookbookRepository.save(cb);
        }

        recipeRepository.delete(recipe);
        return new MessageResponse("Recipe deleted successfully.");
    }

    private RecipeResponse mapToResponse(Recipe recipe) {
        List<RecipeIngredientResponse> ingResponses = recipe.getRecipeIngredients() == null ? Collections.emptyList()
                : recipe.getRecipeIngredients().stream().map(ri -> RecipeIngredientResponse.builder()
                        .id(ri.getIngredient().getId())
                        .name(ri.getIngredient().getName())
                        .icon(ri.getIngredient().getIcon())
                        .quantity(ri.getQuantity())
                        .build()).collect(Collectors.toList());

        return RecipeResponse.builder()
                .id(recipe.getId())
                .name(recipe.getName())
                .image(recipe.getImage())
                .cookTime(recipe.getCookTime())
                .kcal(recipe.getKcal())
                .ingredients(ingResponses)
                .createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt())
                .build();
    }
}
