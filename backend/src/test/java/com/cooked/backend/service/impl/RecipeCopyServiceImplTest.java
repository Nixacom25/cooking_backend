package com.cooked.backend.service.impl;

import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeCopyServiceImplTest {

    @Mock
    private RecipeRepository recipeRepository;

    @InjectMocks
    private RecipeCopyServiceImpl recipeCopyService;

    private final User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
    private final User friend = User.builder().id(UUID.randomUUID()).email("friend@example.com").build();

    private Recipe recipe(User user) {
        return Recipe.builder().id(UUID.randomUUID()).name("Brownie").user(user).isPublic(true).build();
    }

    @Test
    void ownRecipe_isReturnedAsIs() {
        Recipe mine = recipe(friend);

        assertSame(mine, recipeCopyService.copyForUser(mine, friend));
        verifyNoInteractions(recipeRepository);
    }

    @Test
    void existingCopy_isReusedAndRestored() {
        Recipe existing = recipe(friend);
        existing.setDeleted(true);
        when(recipeRepository.findByUserIdAndName(friend.getId(), "Brownie")).thenReturn(Optional.of(existing));
        when(recipeRepository.save(existing)).thenReturn(existing);

        Recipe copy = recipeCopyService.copyForUser(recipe(owner), friend);

        assertSame(existing, copy);
        assertFalse(copy.isDeleted());
    }

    @Test
    void otherUsersRecipe_isClonedAsPrivateExploreCopy() {
        when(recipeRepository.findByUserIdAndName(friend.getId(), "Brownie")).thenReturn(Optional.empty());
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(i -> i.getArgument(0));

        Recipe copy = recipeCopyService.copyForUser(recipe(owner), friend);

        assertSame(friend, copy.getUser());
        assertEquals("Brownie", copy.getName());
        assertFalse(copy.isPublic());
        assertEquals(RecipeOrigin.EXPLORE, copy.getOrigin());
    }
}
