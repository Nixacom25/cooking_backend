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

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInitializationServiceImpl implements UserInitializationService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final CookbookRepository cookbookRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final ActivityLogService activityLogService;

    @Override
    @Async
    @Transactional
    public void initializeAccount(UUID userId) {
        log.info("Starting account initialization for user ID: {}", userId);
        
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.error("User not found for initialization: {}", userId);
                return;
            }

            // Idempotency check: If user already has recipes or cookbooks, they might be partially initialized
            long recipeCount = recipeRepository.countByUserId(user.getId());
            long cookbookCount = cookbookRepository.countByUserId(user.getId());
            
            log.info("Current state for {}: Recipes={}, Cookbooks={}", user.getEmail(), recipeCount, cookbookCount);

            if (recipeCount >= 6 && cookbookCount >= 2) {
                log.info("Account for user {} is already initialized. Skipping.", user.getEmail());
                return;
            }

            /* Removed default cookbook creation as per user request */
            // if (cookbookCount < 2) {
            //     createDefaultCookbooks(user);
            // }

            // 2. Generate Initial Suggested Recipes via AI
            int recipesToGenerate = (int) Math.max(0, 4 - recipeCount);
            if (recipesToGenerate > 0) {
                log.info("Requesting {} AI recipes for {}", recipesToGenerate, user.getEmail());
                List<CreateRecipeRequest> initialRecipes = aiService.generateInitialRecipes(user, recipesToGenerate);

                if (initialRecipes != null && !initialRecipes.isEmpty()) {
                    LocalDateTime expiration = LocalDateTime.now().plusDays(3); 
                    for (CreateRecipeRequest req : initialRecipes) {
                        try {
                            saveSuggestedRecipeForUser(user, req, expiration);
                        } catch (Exception e) {
                            log.error("Failed to save suggested recipe '{}' for {}: {}", req.getName(), user.getEmail(), e.getMessage());
                        }
                    }
                    log.info("Successfully saved {} suggestions for {}", initialRecipes.size(), user.getEmail());
                } else {
                    log.warn("AI service returned 0 recipes for user {}. Check AI service logs.", user.getEmail());
                }
            }

            activityLogService.logActivity(user, "Onboarding Complete", 
                "Your account is ready! We've added some personalized suggestions and cookbooks for you to explore.");

        } catch (Exception e) {
            log.error("Critical error during account initialization for ID {}: {}", userId, e.getMessage(), e);
        }
    }

    private void createDefaultCookbooks(User user) {
        String[] defaultNames = {"Mes Créations", "À Tester"};
        for (String name : defaultNames) {
            if (!cookbookRepository.existsByUserIdAndName(user.getId(), name)) {
                Cookbook cb = Cookbook.builder()
                        .user(user)
                        .name(name)
                        .recipes(new HashSet<>())
                        .build();
                cookbookRepository.save(cb);
                log.info("Created default cookbook '{}' for user: {}", name, user.getEmail());
            }
        }
    }

    private void saveSuggestedRecipeForUser(User user, CreateRecipeRequest request, LocalDateTime expiration) {
        // Build Recipe Entity as Suggested
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
                .equipment(request.getEquipment() != null ? request.getEquipment() : new ArrayList<>())
                .expiresAt(expiration)
                .origin(RecipeOrigin.SUGGESTED)
                .isPublic(true) // Make it visible in Explore for the user
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
    }
}
