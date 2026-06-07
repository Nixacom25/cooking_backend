package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateGroceryItemRequest;
import com.cooked.backend.dto.response.GroceryItemResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.GroceryItem;
import com.cooked.backend.entity.Ingredient;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.GroceryItemRepository;
import com.cooked.backend.repository.IngredientRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroceryItemServiceImplTest {

    @Mock
    private GroceryItemRepository groceryItemRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroceryItemServiceImpl groceryItemService;

    private User dummyUser;
    private Ingredient dummyIngredient;
    private GroceryItem dummyItem;
    private UUID dummyItemId;

    @BeforeEach
    void setUp() {
        dummyUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
                
        dummyIngredient = Ingredient.builder()
                .id(UUID.randomUUID())
                .name("tomato")
                .build();

        dummyItemId = UUID.randomUUID();
        dummyItem = GroceryItem.builder()
                .id(dummyItemId)
                .user(dummyUser)
                .ingredient(dummyIngredient)
                .quantity("2")
                .isBought(false)
                .build();
    }

    @Test
    void testCreateGroceryItem_Success() {
        CreateGroceryItemRequest request = new CreateGroceryItemRequest();
        request.setIngredientName("Tomato");
        request.setQuantity("2");
        request.setPlannedDate(LocalDate.now());

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(ingredientRepository.findByName("tomato")).thenReturn(Optional.of(dummyIngredient));
        when(groceryItemRepository.findByUserIdAndIngredientIdAndPlannedDate(dummyUser.getId(), dummyIngredient.getId(), request.getPlannedDate()))
            .thenReturn(Optional.empty());
        when(groceryItemRepository.save(any(GroceryItem.class))).thenAnswer(i -> {
            GroceryItem item = i.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        GroceryItemResponse response = groceryItemService.create("test@example.com", request);

        assertNotNull(response);
        assertEquals("2", response.getQuantity());
        verify(groceryItemRepository).save(any(GroceryItem.class));
    }

    @Test
    void testToggleBought_Success() {
        when(groceryItemRepository.findById(dummyItemId)).thenReturn(Optional.of(dummyItem));
        when(groceryItemRepository.save(any(GroceryItem.class))).thenAnswer(i -> i.getArgument(0));

        GroceryItemResponse response = groceryItemService.toggleBought(dummyItemId, "test@example.com");

        assertNotNull(response);
        assertTrue(response.getIsBought());
    }

    @Test
    void testDelete_Success() {
        when(groceryItemRepository.findById(dummyItemId)).thenReturn(Optional.of(dummyItem));

        MessageResponse response = groceryItemService.delete(dummyItemId, "test@example.com");

        assertNotNull(response);
        verify(groceryItemRepository).delete(dummyItem);
    }
}
