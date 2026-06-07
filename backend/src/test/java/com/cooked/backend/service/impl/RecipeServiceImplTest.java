package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.IngredientRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.AiService;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.TaxonomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecipeServiceImplTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private CookbookRepository cookbookRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private AiService aiService;
    @Mock
    private TaxonomyService taxonomyService;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private RecipeServiceImpl recipeService;

    private User dummyUser;
    private UUID dummyUserId;
    private Recipe dummyRecipe;
    private UUID dummyRecipeId;

    @BeforeEach
    void setUp() {
        dummyUserId = UUID.randomUUID();
        dummyUser = User.builder()
                .id(dummyUserId)
                .email("test@example.com")
                .build();

        dummyRecipeId = UUID.randomUUID();
        dummyRecipe = Recipe.builder()
                .id(dummyRecipeId)
                .name("Test Recipe")
                .user(dummyUser)
                .origin(RecipeOrigin.MANUAL)
                .build();
    }

    @Test
    void testCreateRecipe_NewRecipe_Success() {
        CreateRecipeRequest request = new CreateRecipeRequest();
        request.setName("New Recipe");
        
        List<IngredientPayload> ingredients = new ArrayList<>();
        IngredientPayload ing = new IngredientPayload();
        ing.setName("Salt");
        ing.setPrice(1.5);
        ingredients.add(ing);
        request.setIngredients(ingredients);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(recipeRepository.findByUserIdAndName(dummyUserId, "New Recipe")).thenReturn(Optional.empty());
        when(taxonomyService.getOrCreateCategory(any(), any())).thenReturn(null);
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> {
            Recipe r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        when(ingredientRepository.findByName("salt")).thenReturn(Optional.empty());
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));

        RecipeResponse response = recipeService.create("test@example.com", request);

        assertNotNull(response);
        assertEquals("New Recipe", response.getName());
        verify(recipeRepository, atLeastOnce()).save(any(Recipe.class));
        verify(activityLogService).logActivity(eq(dummyUser), anyString(), anyString());
    }

    @Test
    void testGetRecipe_Success() {
        when(recipeRepository.findById(dummyRecipeId)).thenReturn(Optional.of(dummyRecipe));

        RecipeResponse response = recipeService.getRecipe(dummyRecipeId, "test@example.com");

        assertNotNull(response);
        assertEquals("Test Recipe", response.getName());
    }

    @Test
    void testGetRecipe_WrongUser_ThrowsException() {
        dummyRecipe.setPublic(false);
        User otherUser = User.builder().email("other@example.com").build();
        dummyRecipe.setUser(otherUser);

        when(recipeRepository.findById(dummyRecipeId)).thenReturn(Optional.of(dummyRecipe));

        assertThrows(BadRequestException.class, () -> recipeService.getRecipe(dummyRecipeId, "test@example.com"));
    }
}
