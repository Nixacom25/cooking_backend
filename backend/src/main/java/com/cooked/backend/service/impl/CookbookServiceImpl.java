package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateCookbookRequest;
import com.cooked.backend.dto.response.CookbookResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.SharedCookbookPreviewResponse;
import com.cooked.backend.entity.Cookbook;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.CookbookService;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.RecipeCopyService;
import com.cooked.backend.dto.response.RecipeIngredientResponse;
import com.cooked.backend.dto.response.RecipeCreatorResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
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
    private final ActivityLogService activityLogService;
    private final RecipeCopyService recipeCopyService;

    private static final String SHARE_BASE_URL = "https://link.cookedapp.com/share/";

    @Override
    public CookbookResponse create(String userEmail, CreateCookbookRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (cookbookRepository.existsByUserIdAndName(user.getId(), request.getName())) {
            throw new BadRequestException("You already have a cookbook named '" + request.getName() + "'.");
        }

        Set<com.cooked.backend.entity.Recipe> recipes = Collections.emptySet();
        if (request.getRecipeIds() != null && !request.getRecipeIds().isEmpty()) {
            recipes = fetchRecipesByIds(request.getRecipeIds());
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
            cb.setRecipes(fetchRecipesByIds(request.getRecipeIds()));
        }

        Cookbook updatedCb = cookbookRepository.save(cb);
        return mapToResponse(updatedCb);
    }

    @Override
    public List<CookbookResponse> getMyCookbooks(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return cookbookRepository.findAllByUserId(user.getId()).stream()
                .map(cb -> mapToResponse(cb))
                .collect(Collectors.toList());
    }

    @Override
    public CookbookResponse getCookbook(UUID id, String userEmail) {
        Cookbook cookbook = findOwned(id, userEmail, "view");

        return mapToResponse(cookbook);
    }

    @Override
    public MessageResponse delete(UUID id, String userEmail) {
        Cookbook cookbook = findOwned(id, userEmail, "delete");

        cookbookRepository.delete(cookbook);
        return new MessageResponse("Cookbook deleted successfully.");
    }

    @Override
    public CookbookResponse togglePin(UUID id, String userEmail) {
        Cookbook cookbook = findOwned(id, userEmail, "pin");

        cookbook.setPinned(!cookbook.isPinned());
        Cookbook saved = cookbookRepository.save(cookbook);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public String getShareLink(UUID id, String userEmail) {
        Cookbook cookbook = findOwned(id, userEmail, "share");
        if (!cookbook.isShared()) {
            cookbook.setShared(true);
            cookbookRepository.save(cookbook);
        }
        return SHARE_BASE_URL + "cookbooks/" + cookbook.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public SharedCookbookPreviewResponse getSharedPreview(UUID id, String userEmail) {
        Cookbook cookbook = findShared(id);
        User owner = cookbook.getUser();
        List<Recipe> recipes = activeRecipes(cookbook);

        return SharedCookbookPreviewResponse.builder()
                .id(cookbook.getId())
                .name(cookbook.getName())
                .ownerName(displayName(owner))
                .recipeCount(recipes.size())
                .coverImages(recipes.stream()
                        .map(Recipe::getImage)
                        .filter(img -> img != null && !img.isBlank())
                        .limit(4)
                        .collect(Collectors.toList()))
                .isOwner(owner.getEmail().equals(userEmail))
                .build();
    }

    @Override
    @Transactional
    public CookbookResponse importShared(UUID id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Cookbook source = findShared(id);

        if (source.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("This cookbook is already yours.");
        }

        // Each recipe becomes the recipient's own private copy, so it shows up
        // in their library and keeps working if the sender deletes theirs.
        Set<Recipe> copies = activeRecipes(source).stream()
                .map(recipe -> recipeCopyService.copyForUser(recipe, user))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Cookbook copy = cookbookRepository.save(Cookbook.builder()
                .user(user)
                .name(availableName(user, source.getName()))
                .recipes(copies)
                .build());

        activityLogService.logActivity(user, "Cookbook Received",
                "Received cookbook '" + copy.getName() + "' with " + copies.size() + " recipes.");

        return mapToResponse(copy);
    }

    private Cookbook findOwned(UUID id, String userEmail, String action) {
        Cookbook cookbook = cookbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cookbook not found"));

        if (!cookbook.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to " + action + " this cookbook.");
        }
        return cookbook;
    }

    /** Unshared cookbooks are reported as missing so their ids can't be probed. */
    private Cookbook findShared(UUID id) {
        return cookbookRepository.findById(id)
                .filter(Cookbook::isShared)
                .orElseThrow(() -> new ResourceNotFoundException("Cookbook not found"));
    }

    private static List<Recipe> activeRecipes(Cookbook cookbook) {
        if (cookbook.getRecipes() == null) return Collections.emptyList();
        return cookbook.getRecipes().stream()
                .filter(Objects::nonNull)
                .filter(r -> !r.isDeleted())
                .collect(Collectors.toList());
    }

    /** "Desserts", then "Desserts (2)", "Desserts (3)"… if the name is taken. */
    private String availableName(User user, String name) {
        String candidate = name;
        for (int i = 2; cookbookRepository.existsByUserIdAndName(user.getId(), candidate); i++) {
            candidate = name + " (" + i + ")";
        }
        return candidate;
    }

    private static String displayName(User user) {
        String first = user.getFirstname() != null ? user.getFirstname().trim() : "";
        String last = user.getLastname() != null ? user.getLastname().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    // One batched IN(...) query instead of one findById() round trip per
    // recipe - create/update used to scale linearly with recipe count,
    // which is exactly what made saving a cookbook with several recipes
    // attached feel slow.
    private Set<com.cooked.backend.entity.Recipe> fetchRecipesByIds(List<UUID> ids) {
        List<com.cooked.backend.entity.Recipe> found = recipeRepository.findAllById(ids);
        if (found.size() != new java.util.HashSet<>(ids).size()) {
            Set<UUID> foundIds = found.stream().map(com.cooked.backend.entity.Recipe::getId).collect(Collectors.toSet());
            UUID missing = ids.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse(null);
            throw new ResourceNotFoundException("Recipe not found with ID: " + missing);
        }
        return new java.util.HashSet<>(found);
    }

    private CookbookResponse mapToResponse(Cookbook cookbook) {
        return CookbookResponse.builder()
                .id(cookbook.getId())
                .name(cookbook.getName())
                .isPinned(cookbook.isPinned())
                .recipes(cookbook.getRecipes() != null ? cookbook.getRecipes().stream()
                        .map(r -> {
                            boolean isInCookbook = true;
                            List<RecipeIngredientResponse> ingResponses = r.getRecipeIngredients() == null
                                    ? Collections.emptyList()
                                    : r.getRecipeIngredients().stream()
                                            .filter(ri -> ri.getIngredient() != null)
                                            .map(ri -> RecipeIngredientResponse.builder()
                                                    .id(ri.getIngredient().getId())
                                                    .name(ri.getIngredient().getName())
                                                    .icon(ri.getIngredient().getIcon())
                                                    .quantity(ri.getQuantity())
                                                    .build())
                                            .collect(Collectors.toList());

                            return RecipeResponse.builder()
                                    .id(r.getId())
                                    .name(r.getName())
                                    .image(r.getImage())
                                    .cookTime(r.getCookTime())
                                    .prepTime(r.getPrepTime())
                                    .kcal(r.getKcal())
                                    .categories(r.getCategories() != null ? r.getCategories().stream().map(com.cooked.backend.entity.RecipeCategory::getName).collect(java.util.stream.Collectors.toList()) : new java.util.ArrayList<>())
                                    .cuisine(r.getCuisine() != null ? r.getCuisine().getName() : null)
                                    .creator(r.getUser() != null ? RecipeCreatorResponse.builder()
                                            .id(r.getUser().getId())
                                            .firstname(r.getUser().getFirstname())
                                            .lastname(r.getUser().getLastname())
                                            .photo(r.getUser().getPhoto())
                                            .build() : null)
                                    .ingredients(ingResponses)
                                    .steps(r.getSteps())
                                    .equipment(new java.util.ArrayList<>(r.getEquipment()))
                                    .servings(r.getServings())
                                    .tips(r.getTips())
                                    .isPublic(r.isPublic())
                                    .isFavorite(false)
                                    .sourceUrl(r.getSourceUrl())
                                    .createdAt(r.getCreatedAt())
                                    .updatedAt(r.getUpdatedAt())
                                    .isSuggested(r.getExpiresAt() != null)
                                    .expiresAt(r.getExpiresAt())
                                    .origin(r.getOrigin() != null ? r.getOrigin().name() : null)
                                    .isInCookbook(isInCookbook)
                                    .isPinned(r.isPinned())
                                    .shareUrl(SHARE_BASE_URL + "recipes/" + r.getId())
                                    .build();
                        })
                        .collect(Collectors.toList()) : Collections.emptyList())
                .createdAt(cookbook.getCreatedAt())
                .updatedAt(cookbook.getUpdatedAt())
                .build();
    }
}
