package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateMealPlanRequest;
import com.cooked.backend.dto.response.MealPlanResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.MealPlan;
import com.cooked.backend.entity.MealType;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.GroceryItemRepository;
import com.cooked.backend.repository.MealPlanRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MealPlanServiceImplTest {

    @Mock
    private MealPlanRepository mealPlanRepository;
    @Mock
    private GroceryItemRepository groceryItemRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private MealPlanServiceImpl mealPlanService;

    private User dummyUser;
    private Recipe dummyRecipe;
    private MealPlan dummyMealPlan;
    private UUID dummyMealPlanId;

    @BeforeEach
    void setUp() {
        dummyUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
                
        dummyRecipe = Recipe.builder()
                .id(UUID.randomUUID())
                .name("Pizza")
                .build();
                
        dummyMealPlanId = UUID.randomUUID();
        dummyMealPlan = MealPlan.builder()
                .id(dummyMealPlanId)
                .user(dummyUser)
                .recipe(dummyRecipe)
                .plannedDate(LocalDate.now())
                .mealType(MealType.DINNER)
                .build();
    }

    @Test
    void testCreateMealPlan_Success() {
        CreateMealPlanRequest request = new CreateMealPlanRequest();
        request.setRecipeId(dummyRecipe.getId());
        request.setPlannedDate(LocalDate.now());
        request.setMealType(MealType.LUNCH);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(recipeRepository.findById(dummyRecipe.getId())).thenReturn(Optional.of(dummyRecipe));
        when(mealPlanRepository.save(any(MealPlan.class))).thenAnswer(i -> {
            MealPlan plan = i.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });

        MealPlanResponse response = mealPlanService.create("test@example.com", request);

        assertNotNull(response);
        assertEquals(MealType.LUNCH, response.getMealType());
        verify(activityLogService).logActivity(eq(dummyUser), anyString(), anyString());
    }

    @Test
    void testDeleteMealPlan_Success() {
        when(mealPlanRepository.findById(dummyMealPlanId)).thenReturn(Optional.of(dummyMealPlan));

        MessageResponse response = mealPlanService.delete(dummyMealPlanId, "test@example.com");

        assertNotNull(response);
        verify(mealPlanRepository).delete(dummyMealPlan);
    }

    @Test
    void testDeleteMealPlan_WrongUser_ThrowsException() {
        User otherUser = User.builder().email("other@example.com").build();
        dummyMealPlan.setUser(otherUser);
        
        when(mealPlanRepository.findById(dummyMealPlanId)).thenReturn(Optional.of(dummyMealPlan));

        assertThrows(BadRequestException.class, () -> mealPlanService.delete(dummyMealPlanId, "test@example.com"));
    }
}
