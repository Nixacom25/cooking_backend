package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.entity.*;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.AiService;
import com.cooked.backend.service.UserInitializationService;
import com.cooked.backend.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInitializationServiceImpl implements UserInitializationService {

    private final CookbookRepository cookbookRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final AiService aiService;
    private final ActivityLogService activityLogService;

    @Override
    @Async
    @Transactional
    public void initializeAccount(User user) {
        log.info("Starting account initialization for user: {}", user.getEmail());
        try {
            // 1. Create Default Cookbooks
            Cookbook favoritesCb = createCookbook(user, "Mes Favoris");
            log.info("Created default cookbook: {}", favoritesCb.getName());
            Cookbook toTryCb = createCookbook(user, "À Tester");

            // 2. Generate 8 Initial Recipes via AI
            List<CreateRecipeRequest> initialRecipes = aiService.generateInitialRecipes(user, 8);

            if (initialRecipes != null && !initialRecipes.isEmpty()) {
                for (CreateRecipeRequest req : initialRecipes) {
                    try {
                        saveRecipeForUser(user, req, toTryCb);
                    } catch (Exception e) {
                        log.error("Failed to save initial recipe '{}' for user: {}", req.getName(), user.getEmail(), e);
                    }
                }
                log.info("Successfully initialized {} recipes for user: {}", initialRecipes.size(), user.getEmail());
            }

            activityLogService.logActivity(user, "Onboarding Complete", 
                "Your account is ready! We've added some personalized recipes to your 'À Tester' cookbook.");

        } catch (Exception e) {
            log.error("Critical error during account initialization for user: {}", user.getEmail(), e);
        }
    }

    private Cookbook createCookbook(User user, String name) {
        Cookbook cb = Cookbook.builder()
                .user(user)
                .name(name)
                .recipes(new HashSet<>())
                .build();
        return cookbookRepository.save(cb);
    }

    private void saveRecipeForUser(User user, CreateRecipeRequest request, Cookbook targetCookbook) {
        // Build Recipe Entity
        Recipe recipe = Recipe.builder()
                .user(user)
                .name(request.getName())
                .image(request.getImage())
                .cookTime(request.getCookTime())
                .kcal(request.getKcal())
                .servings(request.getServings() != null ? request.getServings() : 2)
                .tips(request.getTips())
                .sourceUrl(request.getSourceUrl())
                .steps(request.getSteps() != null ? request.getSteps() : new ArrayList<>())
                .cookbooks(new HashSet<>(Collections.singletonList(targetCookbook)))
                .build();

        // Save Recipe first to get ID
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
        recipeRepository.save(savedRecipe);

        // Update target cookbook
        targetCookbook.getRecipes().add(savedRecipe);
        cookbookRepository.save(targetCookbook);
    }
}
