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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final CookbookRepository cookbookRepository;
    private final ActivityLogService activityLogService;
    private final com.cooked.backend.service.AiService aiService;
    private final com.cooked.backend.service.TaxonomyService taxonomyService;

    @jakarta.annotation.PostConstruct
    public void onStartup() {
        migrateOrigins();
        taxonomyService.migrateExistingRecipes();
        taxonomyService.mergeDuplicateTaxonomies();
    }
    public void migrateOrigins() {
        log.info("Starting recipes origin migration...");
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
            log.info("Successfully migrated {} recipes to SCAN origin.", count);
        } else {
            log.info("No recipes needed migration.");
        }
    }

    @Override
    @Transactional
    public RecipeResponse create(String userEmail, CreateRecipeRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));


        // Handle Duplicates: If recipe with same name exists for this user, reuse it
        String recipeName = request.getName().trim();
        Optional<Recipe> existingRecipe = recipeRepository.findByUserIdAndName(user.getId(), recipeName);
        Recipe recipe;
        
        if (existingRecipe.isPresent()) {
            recipe = existingRecipe.get();
            // Update existing recipe fields if necessary (optional, but good for refresh)
            if (request.getImage() != null) recipe.setImage(request.getImage());
            if (request.getCookTime() != null) recipe.setCookTime(request.getCookTime());
            if (request.getPrepTime() != null) recipe.setPrepTime(request.getPrepTime());
            if (request.getKcal() != null) recipe.setKcal(request.getKcal());
            if (request.getServings() != null) recipe.setServings(request.getServings());
            if (request.getTips() != null) recipe.setTips(request.getTips());
            if (request.getCuisine() != null) recipe.setCuisine(taxonomyService.getOrCreateCategory(request.getCuisine(), CategoryType.CUISINE));
            if (request.getCategory() != null) recipe.setCategory(taxonomyService.getOrCreateCategory(request.getCategory(), CategoryType.CATEGORY));
            if (request.getSourceUrl() != null) recipe.setSourceUrl(request.getSourceUrl());
            if (request.getSteps() != null) recipe.setSteps(request.getSteps());
            if (request.getEquipment() != null) recipe.setEquipment(request.getEquipment());
        } else {
            recipe = Recipe.builder()
                    .user(user)
                    .name(recipeName)
                    .image(request.getImage())
                    .cookTime(request.getCookTime())
                    .prepTime(request.getPrepTime())
                    .kcal(request.getKcal())
                    .servings(request.getServings())
                    .tips(request.getTips())
                    .cuisine(taxonomyService.getOrCreateCategory(request.getCuisine(), CategoryType.CUISINE))
                    .category(taxonomyService.getOrCreateCategory(request.getCategory(), CategoryType.CATEGORY))
                    .sourceUrl(request.getSourceUrl())
                    .steps(request.getSteps() != null ? request.getSteps() : new java.util.ArrayList<>())
                    .equipment(request.getEquipment() != null ? request.getEquipment() : new java.util.ArrayList<>())
                    .origin(request.getOrigin() != null ? RecipeOrigin.valueOf(request.getOrigin().toUpperCase()) : 
                            (request.getSourceUrl() != null && !request.getSourceUrl().isBlank() ? RecipeOrigin.IMPORT : RecipeOrigin.MANUAL))
                    .build();
        }

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
        
        // Link cookbooks to recipe (and vice-versa)
        if (recipe.getCookbooks() == null) {
            recipe.setCookbooks(new HashSet<>());
        }
        recipe.getCookbooks().addAll(attachedCookbooks);

        // Pre-save to generate ID for relations
        Recipe savedRecipe = recipeRepository.save(recipe);

        // Handle Ingredients - Fix: Clear existing collection instead of replacing instance
        if (savedRecipe.getRecipeIngredients() == null) {
            savedRecipe.setRecipeIngredients(new java.util.HashSet<>());
        } else {
            savedRecipe.getRecipeIngredients().clear();
        }
        
        if (request.getIngredients() != null) {
            for (IngredientPayload payload : request.getIngredients()) {
                String ingName = payload.getName().toLowerCase().trim();
                Ingredient ingredient;
                try {
                    ingredient = ingredientRepository.findByName(ingName)
                            .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                                    .name(ingName)
                                    .icon(payload.getIcon())
                                    .build()));
                } catch (Exception e) {
                    // Handle race condition: if another thread saved it just now
                    ingredient = ingredientRepository.findByName(ingName)
                            .orElseThrow(() -> new RuntimeException("Failed to save or find ingredient: " + ingName));
                }
    
                RecipeIngredient ri = RecipeIngredient.builder()
                        .recipe(savedRecipe)
                        .ingredient(ingredient)
                        .quantity((payload.getQuantity() == null || payload.getQuantity().isBlank()) ? "1" : payload.getQuantity())
                        .build();
                savedRecipe.getRecipeIngredients().add(ri);
            }
        }
        savedRecipe = recipeRepository.save(savedRecipe);

        // Ensure inverse relationship is saved in cookbooks
        for (Cookbook cb : attachedCookbooks) {
            if (!cb.getRecipes().contains(savedRecipe)) {
                cb.getRecipes().add(savedRecipe);
                cookbookRepository.save(cb);
            }
        }

        activityLogService.logActivity(user, "New Recipe", "You created the recipe '" + savedRecipe.getName() + "'.");

        return mapToResponse(savedRecipe, user);
    }

    @Override
    public List<RecipeResponse> getMyRecipes(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return recipeRepository.findAllByUserIdAndOriginNot(user.getId(), RecipeOrigin.SUGGESTED).stream()
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
    public Page<RecipeResponse> getExploreRecipes(String cuisine, String category, Pageable pageable) {
        return recipeRepository.findExploreRecipes(RecipeOrigin.EXPLORE, RecipeOrigin.EXPLORE, cuisine, category, pageable)
                .map(recipe -> mapToResponse(recipe, null));
    }

    @Override
    public List<RecipeResponse> getHomeSuggestions(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Optional<Recipe> oldestPersonalRecipe = recipeRepository.findFirstByUserIdAndOriginNotInOrderByCreatedAtAsc(
                user.getId(), 
                List.of(RecipeOrigin.SUGGESTED, RecipeOrigin.EXPLORE)
        );

        if (oldestPersonalRecipe.isPresent()) {
            java.time.LocalDateTime createdAt = oldestPersonalRecipe.get().getCreatedAt();
            if (createdAt.plusDays(3).isBefore(java.time.LocalDateTime.now())) {
                return List.of();
            }
        }

        return recipeRepository.findAllByUserIdAndOriginOrderByCreatedAtDesc(user.getId(), RecipeOrigin.SUGGESTED)
                .stream()
                .map(recipe -> mapToResponse(recipe, null))
                .toList();
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
        
        List<String> preferredCuisines = null;
        if (user != null && user.getFavoriteCuisines() != null && !user.getFavoriteCuisines().isEmpty()) {
            preferredCuisines = user.getFavoriteCuisines();
        }

        // Enforce 10 items limit for "Popular Now" as requested
        org.springframework.data.domain.Pageable limitedPageable = org.springframework.data.domain.PageRequest.of(0, 10);

        try {
            return recipeRepository.findRandomPopularRecipes(category, (preferredCuisines != null && preferredCuisines.isEmpty()) ? null : preferredCuisines, limitedPageable)
                    .map(recipe -> mapToResponse(recipe, user));
        } catch (Exception e) {
            System.err.println("Error fetching popular recipes: " + e.getMessage());
            return recipeRepository.findExploreRecipes(com.cooked.backend.entity.RecipeOrigin.EXPLORE, com.cooked.backend.entity.RecipeOrigin.SUGGESTED, null, category, limitedPageable)
                    .map(recipe -> mapToResponse(recipe, user));
        }
    }

    @Override
    public org.springframework.data.domain.Page<RecipeResponse> getRecentImports(String userEmail,
            org.springframework.data.domain.Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Return all recipes with origin IMPORT, whether validated or not
        return recipeRepository.findByUserIdAndOriginOrderByCreatedAtDesc(user.getId(), RecipeOrigin.IMPORT, pageable)
                .map(recipe -> mapToResponse(recipe, user));
    }

    @Override
    @Transactional
    public RecipeResponse importAndSaveAsSuggestion(String url, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. Extract from AI
        CreateRecipeRequest request = aiService.extractRecipeFromLink(url, userEmail);

        // 2. Map to Entity as a Suggestion with Duplicate Handling
        String recipeName = request.getName().trim();
        Optional<Recipe> existingRecipe = recipeRepository.findByUserIdAndName(user.getId(), recipeName);
        Recipe recipe;

        if (existingRecipe.isPresent()) {
            recipe = existingRecipe.get();
            // Update fields if it's already an IMPORT or SUGGESTED or SCAN
            recipe.setImage(request.getImage());
            recipe.setCookTime(request.getCookTime());
            recipe.setPrepTime(request.getPrepTime());
            recipe.setKcal(request.getKcal());
            recipe.setServings(request.getServings());
            recipe.setTips(request.getTips());
            recipe.setCuisine(taxonomyService.getOrCreateCategory(request.getCuisine(), CategoryType.CUISINE));
            recipe.setCategory(taxonomyService.getOrCreateCategory(request.getCategory(), CategoryType.CATEGORY));
            recipe.setSourceUrl(url);
            recipe.setSteps(request.getSteps() != null ? request.getSteps() : new ArrayList<>());
            recipe.setEquipment(request.getEquipment() != null ? request.getEquipment() : new ArrayList<>());
            
            // If it was already permanent, keep it permanent. If not, refresh expiry.
            if (recipe.getExpiresAt() != null) {
                recipe.setExpiresAt(java.time.LocalDateTime.now().plusDays(3));
            }
        } else {
            recipe = Recipe.builder()
                    .user(user)
                    .name(recipeName)
                    .image(request.getImage())
                    .cookTime(request.getCookTime())
                    .prepTime(request.getPrepTime())
                    .kcal(request.getKcal())
                    .servings(request.getServings())
                    .tips(request.getTips())
                    .cuisine(taxonomyService.getOrCreateCategory(request.getCuisine(), CategoryType.CUISINE))
                    .category(taxonomyService.getOrCreateCategory(request.getCategory(), CategoryType.CATEGORY))
                    .sourceUrl(url)
                    .steps(request.getSteps() != null ? request.getSteps() : new ArrayList<>())
                    .equipment(request.getEquipment() != null ? request.getEquipment() : new ArrayList<>())
                    .origin(RecipeOrigin.IMPORT)
                    .expiresAt(java.time.LocalDateTime.now().plusDays(3)) // Suggested for 3 days
                    .build();
        }

        Recipe saved = recipeRepository.save(recipe);

        // 3. Save ingredients
        if (request.getIngredients() != null) {
            if (saved.getRecipeIngredients() == null) {
                saved.setRecipeIngredients(new HashSet<>());
            } else {
                saved.getRecipeIngredients().clear();
            }

            for (com.cooked.backend.dto.request.IngredientPayload payload : request.getIngredients()) {
                String ingName = payload.getName().toLowerCase().trim();
                Ingredient ingredient;
                try {
                    ingredient = ingredientRepository.findByName(ingName)
                            .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                                    .name(ingName)
                                    .icon(payload.getIcon())
                                    .build()));
                } catch (Exception e) {
                    // Handle race condition: if another thread saved it just now
                    ingredient = ingredientRepository.findByName(ingName)
                            .orElseThrow(() -> new RuntimeException("Failed to save or find ingredient: " + ingName));
                }

                saved.getRecipeIngredients().add(RecipeIngredient.builder()
                        .recipe(saved)
                        .ingredient(ingredient)
                        .quantity((payload.getQuantity() == null || payload.getQuantity().isBlank()) ? "1" : payload.getQuantity())
                        .build());
            }
            saved = recipeRepository.save(saved);
        }

        return mapToResponse(saved, user);
    }

    @Override
    @Transactional
    public RecipeResponse validateSuggestedRecipe(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.getUser().getId().equals(user.getId())) {
            if (recipe.isPublic()) {
                // Check for duplicate by name for this user to avoid multiple clones
                Optional<Recipe> existing = recipeRepository.findByUserIdAndName(user.getId(), recipe.getName());
                if (existing.isPresent()) {
                    return mapToResponse(existing.get(), user);
                }
                
                Recipe clone = cloneRecipeForUser(recipe, user);
                activityLogService.logActivity(user, "Recipe Saved", "You saved '" + clone.getName() + "' from Explore.");
                return mapToResponse(clone, user);
            }
            throw new BadRequestException("You do not have permission to validate this recipe.");
        }

        // When validating a suggestion, it becomes a "MANUAL" or "SCAN" recipe but not a suggestion anymore
        // BUT we must keep IMPORT as IMPORT so it stays in Recent Imports list
        if (recipe.getOrigin() != RecipeOrigin.IMPORT) {
            recipe.setOrigin(RecipeOrigin.SCAN);
        }
        recipe.setExpiresAt(null);

        Recipe saved = recipeRepository.save(recipe);

        activityLogService.logActivity(user, "Recipe Validated", "You saved the suggestion '" + saved.getName() + "'.");

        return mapToResponse(saved, user);
    }

    private Recipe cloneRecipeForUser(Recipe original, User user) {
        Recipe clone = Recipe.builder()
                .user(user)
                .name(original.getName())
                .image(original.getImage())
                .cookTime(original.getCookTime())
                .prepTime(original.getPrepTime())
                .kcal(original.getKcal())
                .category(original.getCategory())
                .cuisine(original.getCuisine())
                .servings(original.getServings())
                .tips(original.getTips())
                .steps(new ArrayList<>(original.getSteps()))
                .equipment(new ArrayList<>(original.getEquipment()))
                .isPublic(false) // Cloned recipes are private for the user
                .origin(RecipeOrigin.SCAN)
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

    private RecipeResponse mapToResponse(Recipe recipe, User user) {
        boolean isInCookbook = false;
        if (user != null) {
            isInCookbook = recipe.getCookbooks() != null && !recipe.getCookbooks().isEmpty();
        }

        List<RecipeIngredientResponse> ingResponses = recipe.getRecipeIngredients() == null ? Collections.emptyList()
                : recipe.getRecipeIngredients().stream()
                        .filter(ri -> ri.getIngredient() != null)
                        .map(ri -> RecipeIngredientResponse.builder()
                                .id(ri.getIngredient().getId())
                                .name(ri.getIngredient().getName())
                                .icon(ri.getIngredient().getIcon())
                                .quantity(ri.getQuantity())
                                .build()).collect(Collectors.toList());

        return RecipeResponse.builder()
                .id(recipe.getId())
                .name(recipe.getName())
                .isPinned(recipe.isPinned())
                .image(recipe.getImage())
                .cookTime(recipe.getCookTime())
                .prepTime(recipe.getPrepTime())
                .kcal(recipe.getKcal())
                .category(recipe.getCategory() != null ? recipe.getCategory().getName() : null)
                .cuisine(recipe.getCuisine() != null ? recipe.getCuisine().getName() : null)
                .creator(recipe.getUser() != null ? RecipeCreatorResponse.builder()
                        .id(recipe.getUser().getId())
                        .firstname(recipe.getUser().getFirstname())
                        .lastname(recipe.getUser().getLastname())
                        .photo(recipe.getUser().getPhoto())
                        .build() : null)
                .ingredients(ingResponses)
                .steps(recipe.getSteps())
                .equipment(new java.util.ArrayList<>(recipe.getEquipment()))
                .servings(recipe.getServings())
                .tips(recipe.getTips())
                .isPublic(recipe.isPublic())
                .isFavorite(false)
                .sourceUrl(recipe.getSourceUrl())
                .createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt())
                .isSuggested(recipe.getExpiresAt() != null)
                .expiresAt(recipe.getExpiresAt())
                .origin(recipe.getOrigin() != null ? recipe.getOrigin().name() : null)
                .isInCookbook(isInCookbook)
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

    @Override
    public List<com.cooked.backend.dto.response.ExploreTaxonomyResponse> getExploreCuisines() {
        return recipeRepository.findCuisinesWithCount().stream()
                .map(obj -> com.cooked.backend.dto.response.ExploreTaxonomyResponse.builder()
                        .name((String) obj[0])
                        .image((String) obj[1])
                        .recipeCount((Long) obj[2])
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<com.cooked.backend.dto.response.ExploreTaxonomyResponse> getExploreCategories() {
        return recipeRepository.findCategoriesWithCount().stream()
                .map(obj -> com.cooked.backend.dto.response.ExploreTaxonomyResponse.builder()
                        .name((String) obj[0])
                        .image((String) obj[1])
                        .recipeCount((Long) obj[2])
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RecipeResponse togglePin(UUID id, String userEmail) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

        if (!recipe.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to pin this recipe.");
        }

        recipe.setPinned(!recipe.isPinned());
        Recipe saved = recipeRepository.save(recipe);

        User user = userRepository.findByEmail(userEmail).orElse(null);
        return mapToResponse(saved, user);
    }

    // Removed private getOrCreateCategory, now using taxonomyService
}
