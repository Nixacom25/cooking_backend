package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateMealPlanRequest;
import com.cooked.backend.dto.response.MealPlanResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.RecipeIngredientResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.GroceryItemRepository;
import com.cooked.backend.repository.MealPlanRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.MealPlanService;
import com.cooked.backend.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MealPlanServiceImpl implements MealPlanService {

        private final MealPlanRepository mealPlanRepository;
        private final GroceryItemRepository groceryItemRepository;
        private final RecipeRepository recipeRepository;
        private final UserRepository userRepository;
        private final ActivityLogService activityLogService;

        @Override
        @Transactional
        public MealPlanResponse create(String userEmail, CreateMealPlanRequest request) {
                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                Recipe recipe = recipeRepository.findById(request.getRecipeId())
                                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

                MealPlan mealPlan = MealPlan.builder()
                                .user(user)
                                .recipe(recipe)
                                .plannedDate(request.getPlannedDate())
                                .mealType(request.getMealType())
                                .build();

                MealPlan savedPlan = mealPlanRepository.save(mealPlan);

                // Auto-generate Grocery Items
                if (recipe.getRecipeIngredients() != null) {
                        for (RecipeIngredient ri : recipe.getRecipeIngredients()) {
                                GroceryItem item = GroceryItem.builder()
                                                .user(user)
                                                .ingredient(ri.getIngredient())
                                                .recipe(recipe)
                                                .quantity(ri.getQuantity())
                                                .isBought(false)
                                                .plannedDate(request.getPlannedDate())
                                                .build();
                                groceryItemRepository.save(item);
                        }
                }

                activityLogService.logActivity(user, "Meal Planned", "Scheduled " + recipe.getName() + " for "
                                + request.getPlannedDate() + " (" + request.getMealType() + ").");

                return mapToResponse(savedPlan);
        }

        @Override
        public List<MealPlanResponse> getMyMealPlans(String userEmail) {
                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                return mealPlanRepository.findAllByUserId(user.getId()).stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<MealPlanResponse> getMyMealPlansByDate(String userEmail, LocalDate date) {
                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                return mealPlanRepository.findAllByUserIdAndPlannedDate(user.getId(), date).stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public MessageResponse delete(UUID id, String userEmail) {
                MealPlan mealPlan = mealPlanRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Meal Plan not found"));

                if (!mealPlan.getUser().getEmail().equals(userEmail)) {
                        throw new BadRequestException("You do not have permission to delete this meal plan.");
                }

                mealPlanRepository.delete(mealPlan);
                return new MessageResponse("Meal plan deleted successfully.");
        }

        private MealPlanResponse mapToResponse(MealPlan plan) {
                Recipe recipe = plan.getRecipe();
                List<RecipeIngredientResponse> ingResponses = recipe.getRecipeIngredients() == null
                                ? Collections.emptyList()
                                : recipe.getRecipeIngredients().stream().map(ri -> RecipeIngredientResponse.builder()
                                                .id(ri.getIngredient().getId())
                                                .name(ri.getIngredient().getName())
                                                .icon(ri.getIngredient().getIcon())
                                                .quantity(ri.getQuantity())
                                                .build()).collect(Collectors.toList());

                RecipeResponse recipeResponse = RecipeResponse.builder()
                                .id(recipe.getId())
                                .name(recipe.getName())
                                .image(recipe.getImage())
                                .cookTime(recipe.getCookTime())
                                .kcal(recipe.getKcal())
                                .ingredients(ingResponses)
                                .build();

                return MealPlanResponse.builder()
                                .id(plan.getId())
                                .recipe(recipeResponse)
                                .plannedDate(plan.getPlannedDate())
                                .mealType(plan.getMealType())
                                .createdAt(plan.getCreatedAt())
                                .updatedAt(plan.getUpdatedAt())
                                .build();
        }
}
