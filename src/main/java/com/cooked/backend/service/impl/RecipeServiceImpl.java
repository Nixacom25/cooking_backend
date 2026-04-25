package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeCreatorResponse;
import com.cooked.backend.dto.response.RecipeIngredientResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.RecipeService;
import com.cooked.backend.service.ActivityLogService;
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
    private final ActivityLogService activityLogService;
    private final FavoriteRecipeRepository favoriteRecipeRepository;

    @jakarta.annotation.PostConstruct
    public void migrateOrigins() {
        System.out.println("Starting recipes origin migration...");
        List<Recipe> recipes = recipeRepository.findAll();
        long count = 0;
        for (Recipe r : recipes) {
            if (r.getOrigin() == RecipeOrigin.MANUAL || r.getOrigin() == null) {
                r.setOrigin(RecipeOrigin.SCAN);
                count++;
            }
        }
        if (count > 0) {
            recipeRepository.saveAll(recipes);
            System.out.println("Successfully migrated " + count + " recipes to SCAN origin.");
        } else {
            System.out.println("No recipes needed migration.");
        }
    }

    @Override
    @Transactional
    public RecipeResponse create(String userEmail, CreateRecipeRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));


        Recipe recipe = Recipe.builder()
                .user(user)
                .name(request.getName())
                .image(request.getImage())
                .cookTime(request.getCookTime())
                .prepTime(request.getPrepTime())
                .kcal(request.getKcal())
                .servings(request.getServings())
                .tips(request.getTips())
                .sourceUrl(request.getSourceUrl())
                .steps(request.getSteps() != null ? request.getSteps() : new java.util.ArrayList<>())
                .equipment(request.getEquipment() != null ? request.getEquipment() : new java.util.ArrayList<>())
                .origin(request.getOrigin() != null ? RecipeOrigin.valueOf(request.getOrigin().toUpperCase()) : 
                        (request.getSourceUrl() != null && !request.getSourceUrl().isBlank() ? RecipeOrigin.IMPORT : RecipeOrigin.MANUAL))
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
                        .quantity((payload.getQuantity() == null || payload.getQuantity().isBlank()) ? "1" : payload.getQuantity())
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

        activityLogService.logActivity(user, "New Recipe", "You created the recipe '" + savedRecipe.getName() + "'.");

        return mapToResponse(savedRecipe, user);
    }

    @Override
    public List<RecipeResponse> getMyRecipes(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return recipeRepository.findAllByUserId(user.getId()).stream()
                .map(recipe -> mapToResponse(recipe, user))
                .collect(Collectors.toList());
    }

    @Override
    public RecipeResponse getRecipe(UUID id, String userEmail) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.getUser().getEmail().equals(userEmail) && !recipe.isPublic()) {
            throw new BadRequestException("You do not have permission to view this recipe.");
        }

        User user = userRepository.findByEmail(userEmail).orElse(null);
        return mapToResponse(recipe, user);
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

    @Override
    @Transactional
    public MessageResponse togglePublicVisibility(UUID id, String userEmail) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to modify this recipe.");
        }

        recipe.setPublic(!recipe.isPublic());
        recipeRepository.save(recipe);

        String status = recipe.isPublic() ? "public" : "private";
        return new MessageResponse("Recipe is now " + status);
    }

    @Override
    public org.springframework.data.domain.Page<RecipeResponse> getExploreRecipes(
            org.springframework.data.domain.Pageable pageable) {
        return recipeRepository.findByIsPublicTrueOrderByCreatedAtDesc(pageable)
                .map(recipe -> mapToResponse(recipe, null));
    }

    @Override
    @Transactional
    public MessageResponse toggleFavorite(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.isPublic() && !recipe.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("You cannot favorite a private recipe that is not yours.");
        }

        Optional<FavoriteRecipe> existing = favoriteRecipeRepository.findByUserAndRecipe(user, recipe);
        if (existing.isPresent()) {
            favoriteRecipeRepository.delete(existing.get());
            return new MessageResponse("Recipe removed from favorites");
        } else {
            FavoriteRecipe favorite = FavoriteRecipe.builder()
                    .user(user)
                    .recipe(recipe)
                    .build();
            favoriteRecipeRepository.save(favorite);
            return new MessageResponse("Recipe added to favorites");
        }
    }

    @Override
    public org.springframework.data.domain.Page<RecipeResponse> getFavoriteRecipes(String userEmail,
            org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return favoriteRecipeRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(fav -> mapToResponse(fav.getRecipe(), user));
    }

    @Override
    public org.springframework.data.domain.Page<com.cooked.backend.dto.response.CreatorResponse> getTopCreators(
            org.springframework.data.domain.Pageable pageable) {
        return recipeRepository.findTopCreators(pageable)
                .map(r -> com.cooked.backend.dto.response.CreatorResponse.builder()
                        .id(((User) r[0]).getId())
                        .firstname(((User) r[0]).getFirstname())
                        .lastname(((User) r[0]).getLastname())
                        .photo(((User) r[0]).getPhoto())
                        .publicRecipeCount((long) r[1])
                        .totalUsageCount((long) r[2])
                        .build());
    }

    @Override
    public org.springframework.data.domain.Page<RecipeResponse> getPopularRecipes(String category, String userEmail,
            org.springframework.data.domain.Pageable pageable) {
        User user = userEmail != null ? userRepository.findByEmail(userEmail).orElse(null) : null;
        return recipeRepository.findPopularRecipes(category, pageable)
                .map(r -> mapToResponse((Recipe) r[0], user));
    }

    @Override
    public org.springframework.data.domain.Page<RecipeResponse> getRecentImports(String userEmail,
            org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return recipeRepository.findByUserIdAndOriginOrderByCreatedAtDesc(user.getId(), RecipeOrigin.IMPORT, pageable)
                .map(recipe -> mapToResponse(recipe, user));
    }

    @Override
    @Transactional
    public RecipeResponse validateSuggestedRecipe(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("You do not have permission to validate this recipe.");
        }

        // When validating a suggestion, it becomes a "MANUAL" or "SCAN" recipe but not a suggestion anymore
        // Actually, we'll keep it as SCAN if it was suggested? 
        // User says "marque toutes les recipes deja creer par default a scan"
        recipe.setOrigin(RecipeOrigin.SCAN);
        recipe.setExpiresAt(null);

        Recipe saved = recipeRepository.save(recipe);

        activityLogService.logActivity(user, "Recipe Validated", "You saved the suggestion '" + saved.getName() + "'.");

        return mapToResponse(saved, user);
    }

    private RecipeResponse mapToResponse(Recipe recipe, User user) {
        boolean isFavorite = false;
        if (user != null) {
            isFavorite = favoriteRecipeRepository.existsByUserAndRecipe(user, recipe);
        }

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
                .prepTime(recipe.getPrepTime())
                .kcal(recipe.getKcal())
                .category(recipe.getCategory())
                .creator(RecipeCreatorResponse.builder()
                        .id(recipe.getUser().getId())
                        .firstname(recipe.getUser().getFirstname())
                        .lastname(recipe.getUser().getLastname())
                        .photo(recipe.getUser().getPhoto())
                        .build())
                .ingredients(ingResponses)
                .steps(recipe.getSteps())
                .equipment(new java.util.ArrayList<>(recipe.getEquipment()))
                .servings(recipe.getServings())
                .tips(recipe.getTips())
                .isPublic(recipe.isPublic())
                .isFavorite(isFavorite)
                .sourceUrl(recipe.getSourceUrl())
                .createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt())
                .isSuggested(recipe.getOrigin() == RecipeOrigin.SUGGESTED)
                .expiresAt(recipe.getExpiresAt())
                .origin(recipe.getOrigin() != null ? recipe.getOrigin().name() : null)
                .shareUrl("https://cooked.nixacom.com/recipes/" + recipe.getId())
                .build();
    }
    @Override
    @Transactional
    public String getShareLink(UUID id, String userEmail) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        // Mark as public if not already
        if (!recipe.isPublic()) {
            recipe.setPublic(true);
            recipeRepository.save(recipe);
        }

        return "https://cooked.nixacom.com/recipes/" + recipe.getId();
    }
}
