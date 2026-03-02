package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateGroceryItemRequest;
import com.cooked.backend.dto.response.GroceryItemResponse;
import com.cooked.backend.dto.response.IngredientResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.GroceryItem;
import com.cooked.backend.entity.Ingredient;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.GroceryItemRepository;
import com.cooked.backend.repository.IngredientRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.GroceryItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroceryItemServiceImpl implements GroceryItemService {

    private final GroceryItemRepository groceryItemRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    @Override
    public GroceryItemResponse create(String userEmail, CreateGroceryItemRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Ingredient ingredient = ingredientRepository.findByName(request.getIngredientName().toLowerCase())
                .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                        .name(request.getIngredientName().toLowerCase())
                        .icon(request.getIngredientIcon())
                        .build()));

        Recipe recipe = null;
        if (request.getRecipeId() != null) {
            recipe = recipeRepository.findById(request.getRecipeId())
                    .orElse(null);
        }

        GroceryItem item = GroceryItem.builder()
                .user(user)
                .ingredient(ingredient)
                .recipe(recipe)
                .quantity(request.getQuantity())
                .isBought(false)
                .plannedDate(request.getPlannedDate())
                .build();

        return mapToResponse(groceryItemRepository.save(item));
    }

    @Override
    public List<GroceryItemResponse> getMyGroceryItems(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return groceryItemRepository.findAllByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<GroceryItemResponse> getMyGroceryItemsByDate(String userEmail, LocalDate date) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return groceryItemRepository.findAllByUserIdAndPlannedDate(user.getId(), date).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public GroceryItemResponse toggleBought(UUID id, String userEmail) {
        GroceryItem item = groceryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery Item not found"));

        if (!item.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to modify this grocery item.");
        }

        item.setIsBought(!item.getIsBought());
        return mapToResponse(groceryItemRepository.save(item));
    }

    @Override
    public MessageResponse delete(UUID id, String userEmail) {
        GroceryItem item = groceryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grocery Item not found"));

        if (!item.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to delete this grocery item.");
        }

        groceryItemRepository.delete(item);
        return new MessageResponse("Grocery item deleted successfully.");
    }

    private GroceryItemResponse mapToResponse(GroceryItem item) {
        IngredientResponse ingredientResponse = IngredientResponse.builder()
                .id(item.getIngredient().getId())
                .name(item.getIngredient().getName())
                .icon(item.getIngredient().getIcon())
                .build();

        return GroceryItemResponse.builder()
                .id(item.getId())
                .ingredient(ingredientResponse)
                .recipeId(item.getRecipe() != null ? item.getRecipe().getId() : null)
                .quantity(item.getQuantity())
                .isBought(item.getIsBought())
                .plannedDate(item.getPlannedDate())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
