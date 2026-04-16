package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateCookbookRequest;
import com.cooked.backend.dto.response.CookbookResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.Cookbook;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.FavoriteRecipeRepository;
import com.cooked.backend.service.CookbookService;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.dto.response.RecipeIngredientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CookbookServiceImpl implements CookbookService {

    private final CookbookRepository cookbookRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final ActivityLogService activityLogService;

    @Override
    public CookbookResponse create(String userEmail, CreateCookbookRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (cookbookRepository.existsByUserIdAndName(user.getId(), request.getName())) {
            throw new BadRequestException("You already have a cookbook named '" + request.getName() + "'.");
        }

        Set<com.cooked.backend.entity.Recipe> recipes = Collections.emptySet();
        if (request.getRecipeIds() != null && !request.getRecipeIds().isEmpty()) {
            recipes = request.getRecipeIds().stream()
                    .map(id -> recipeRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with ID: " + id)))
                    .collect(Collectors.toSet());
        }

        Cookbook cb = Cookbook.builder()
                .user(user)
                .name(request.getName())
                .recipes(recipes)
                .build();

        Cookbook savedCb = cookbookRepository.save(cb);

        activityLogService.logActivity(user, "Cookbook Created",
                "Successfully created cookbook named '" + request.getName() + "'.");

        return mapToResponse(savedCb);
    }

    @Override
    public CookbookResponse update(UUID id, String userEmail, CreateCookbookRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Cookbook cb = cookbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cookbook not found"));

        if (!cb.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("You do not have permission to update this cookbook.");
        }

        // Check name uniqueness if changed
        if (!cb.getName().equalsIgnoreCase(request.getName())) {
            if (cookbookRepository.existsByUserIdAndName(user.getId(), request.getName())) {
                throw new BadRequestException("You already have a cookbook named '" + request.getName() + "'.");
            }
            cb.setName(request.getName());
        }

        if (request.getRecipeIds() != null) {
            Set<com.cooked.backend.entity.Recipe> recipes = request.getRecipeIds().stream()
                    .map(rid -> recipeRepository.findById(rid)
                            .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with ID: " + rid)))
                    .collect(Collectors.toSet());
            cb.setRecipes(recipes);
        }

        Cookbook updatedCb = cookbookRepository.save(cb);
        return mapToResponse(updatedCb);
    }

    @Override
    public List<CookbookResponse> getMyCookbooks(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return cookbookRepository.findAllByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CookbookResponse getCookbook(UUID id, String userEmail) {
        Cookbook cookbook = cookbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cookbook not found"));

        if (!cookbook.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to view this cookbook.");
        }

        return mapToResponse(cookbook);
    }

    @Override
    public MessageResponse delete(UUID id, String userEmail) {
        Cookbook cookbook = cookbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cookbook not found"));

        if (!cookbook.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to delete this cookbook.");
        }

        cookbookRepository.delete(cookbook);
        return new MessageResponse("Cookbook deleted successfully.");
    }

    private CookbookResponse mapToResponse(Cookbook cookbook) {
        User user = cookbook.getUser();
        return CookbookResponse.builder()
                .id(cookbook.getId())
                .name(cookbook.getName())
                .recipes(cookbook.getRecipes() != null ? cookbook.getRecipes().stream()
                        .map(r -> {
                            boolean isFavorite = favoriteRecipeRepository.existsByUserAndRecipe(user, r);
                            List<RecipeIngredientResponse> ingResponses = r.getRecipeIngredients() == null
                                    ? Collections.emptyList()
                                    : r.getRecipeIngredients().stream().map(ri -> RecipeIngredientResponse.builder()
                                            .id(ri.getIngredient().getId())
                                            .name(ri.getIngredient().getName())
                                            .icon(ri.getIngredient().getIcon())
                                            .quantity(ri.getQuantity())
                                            .build()).collect(Collectors.toList());

                            return com.cooked.backend.dto.response.RecipeResponse.builder()
                                    .id(r.getId())
                                    .name(r.getName())
                                    .image(r.getImage())
                                    .cookTime(r.getCookTime())
                                    .kcal(r.getKcal())
                                    .ingredients(ingResponses)
                                    .steps(r.getSteps())
                                    .isPublic(r.isPublic())
                                    .isFavorite(isFavorite)
                                    .createdAt(r.getCreatedAt())
                                    .updatedAt(r.getUpdatedAt())
                                    .build();
                        })
                        .collect(Collectors.toList()) : Collections.emptyList())
                .createdAt(cookbook.getCreatedAt())
                .updatedAt(cookbook.getUpdatedAt())
                .build();
    }
}
