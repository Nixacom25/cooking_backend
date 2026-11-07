package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ImageMatchPath;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeImageMatch;
import com.cooked.backend.repository.ImageLibraryRepository;
import com.cooked.backend.repository.RecipeImageMatchRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.ImageCandidateProvider;
import com.cooked.backend.service.RecipeTagExtractor;
import com.cooked.backend.service.VisionImageRankerService;
import com.cooked.backend.service.dto.RecipeTags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeImageMatchingServiceImplTest {

    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private ImageLibraryRepository imageLibraryRepository;
    @Mock
    private RecipeImageMatchRepository recipeImageMatchRepository;
    @Mock
    private RecipeTagExtractor tagExtractor;
    @Mock
    private ImageCandidateProvider candidateProvider;
    @Mock
    private VisionImageRankerService visionRanker;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private RecipeImageMatchingServiceImpl service;

    @Test
    void leavesImageUnsetWhenNoVerifiedMatchExists() {
        UUID id = UUID.randomUUID();
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName("Chicken stew");
        RecipeTags tags = RecipeTags.builder()
                .protein("chicken")
                .dishType("stew")
                .cuisine("Italian")
                .mainIngredients(List.of("chicken"))
                .build();
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));
        when(tagExtractor.extract(recipe)).thenReturn(tags);
        when(tagExtractor.broaden(tags)).thenReturn(tags);
        when(imageLibraryRepository.findAll()).thenReturn(List.of());
        when(candidateProvider.searchCandidates(anyString(), anyInt())).thenReturn(List.of());

        service.matchAndAssignImageAsync(id);

        assertThat(recipe.getImage()).isNull();
        verify(recipeRepository, never()).save(any(Recipe.class));
        verify(recipeRepository, never()).findByCuisineWithImage(anyString(), any());
        verify(recipeRepository, never()).findByCategoryWithImage(anyString(), any());
        verify(recipeImageMatchRepository).save(argThat(match ->
                match.getPath() == ImageMatchPath.FALLBACK && match.getChosenImageUrl() == null));
    }
}
