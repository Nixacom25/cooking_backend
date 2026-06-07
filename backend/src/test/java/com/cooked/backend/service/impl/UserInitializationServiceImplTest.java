package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.IngredientRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserInitializationServiceImplTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private CookbookRepository cookbookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiService aiService;
    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private UserInitializationServiceImpl initializationService;

    private User dummyUser;

    @BeforeEach
    void setUp() {
        dummyUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
    }

    @Test
    void testInitializeAccount_AlreadyInitialized_Skips() {
        when(userRepository.findById(dummyUser.getId())).thenReturn(Optional.of(dummyUser));
        when(recipeRepository.countByUserId(dummyUser.getId())).thenReturn(6L);
        when(cookbookRepository.countByUserId(dummyUser.getId())).thenReturn(2L);

        initializationService.initializeAccount(dummyUser.getId());

        verify(aiService, never()).generateInitialRecipes(any(), anyInt());
    }

    @Test
    void testInitializeAccount_GeneratesRecipes() {
        when(userRepository.findById(dummyUser.getId())).thenReturn(Optional.of(dummyUser));
        when(recipeRepository.countByUserId(dummyUser.getId())).thenReturn(0L);
        when(cookbookRepository.countByUserId(dummyUser.getId())).thenReturn(0L);

        CreateRecipeRequest req = new CreateRecipeRequest();
        req.setName("Test AI Recipe");
        
        when(aiService.generateInitialRecipes(eq(dummyUser), anyInt())).thenReturn(List.of(req));
        
        when(recipeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        initializationService.initializeAccount(dummyUser.getId());

        verify(aiService).generateInitialRecipes(eq(dummyUser), eq(4));
        verify(recipeRepository, atLeastOnce()).save(any());
        verify(userRepository).save(any(User.class));
    }
}
