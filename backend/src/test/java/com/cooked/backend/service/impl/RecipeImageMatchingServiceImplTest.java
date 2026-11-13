package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ImageMatchPath;
import com.cooked.backend.entity.ImageLibrary;
import com.cooked.backend.entity.ImageSource;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.repository.ImageLibraryRepository;
import com.cooked.backend.repository.RecipeImageMatchRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.RecipeTagExtractor;
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

    @InjectMocks
    private RecipeImageMatchingServiceImpl service;

    @Test
    void selectsMatchingDatabaseImageAndPrefersTheLeastUsedUniquePhoto() {
        UUID id = UUID.randomUUID();
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName("Chicken Stew");
        RecipeTags tags = RecipeTags.builder()
                .protein("chicken")
                .dishType("stew")
                .mainIngredients(List.of("tomato", "onion"))
                .build();
        ImageLibrary highlyUsed = libraryImage("https://images.test/used.jpg", "chicken", "stew",
                "tomato,onion", 8);
        ImageLibrary leastUsed = libraryImage("https://images.test/fresh.jpg", "chicken", "stew",
                "tomato,onion", 0);
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));
        when(tagExtractor.extract(recipe)).thenReturn(tags);
        when(imageLibraryRepository.findAll()).thenReturn(List.of(highlyUsed, leastUsed));

        service.matchAndAssignImageAsync(id);

        assertThat(recipe.getImage()).isEqualTo("https://images.test/fresh.jpg");
        assertThat(leastUsed.getTimesUsed()).isEqualTo(1);
        assertThat(highlyUsed.getTimesUsed()).isEqualTo(8);
        verify(recipeRepository).save(recipe);
        verify(imageLibraryRepository).save(leastUsed);
        verifyNoMoreInteractions(recipeRepository);
        verify(recipeImageMatchRepository).save(argThat(match ->
                match.getPath() == ImageMatchPath.LIBRARY
                        && "https://images.test/fresh.jpg".equals(match.getChosenImageUrl())));
    }

    @Test
    void rejectsLibraryImageForAConflictingProteinEvenWhenCuisineAndDishMatch() {
        UUID id = UUID.randomUUID();
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName("Chicken stew");
        recipe.setImage("https://images.test/unverified.jpg");
        RecipeTags tags = RecipeTags.builder()
                .protein("chicken")
                .dishType("stew")
                .cuisine("Italian")
                .mainIngredients(List.of("tomato", "onion"))
                .build();
        ImageLibrary fish = libraryImage("https://images.test/fish-stew.jpg", "fish", "stew",
                "tomato,onion", 0);
        fish.setCuisine("Italian");
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));
        when(tagExtractor.extract(recipe)).thenReturn(tags);
        when(imageLibraryRepository.findAll()).thenReturn(List.of(fish));

        service.matchAndAssignImageAsync(id);

        assertThat(recipe.getImage()).isEqualTo("https://images.test/unverified.jpg");
        verify(recipeRepository, never()).save(any(Recipe.class));
        verify(recipeImageMatchRepository).save(argThat(match ->
                match.getPath() == ImageMatchPath.FALLBACK && match.getChosenImageUrl() == null));
    }

    @Test
    void acceptsAnExactDatabaseRecipeTitleWhenTagsAreNotAvailable() {
        UUID id = UUID.randomUUID();
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName("Chicken Tacos");
        ImageLibrary entry = ImageLibrary.builder()
                .imageUrl("https://images.test/chicken-tacos.jpg")
                .recipeTitle("Chicken Tacos")
                .source(ImageSource.BACKFILL)
                .build();
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));
        when(tagExtractor.extract(recipe)).thenReturn(RecipeTags.builder().build());
        when(imageLibraryRepository.findAll()).thenReturn(List.of(entry));

        service.matchAndAssignImageAsync(id);

        assertThat(recipe.getImage()).isEqualTo(entry.getImageUrl());
        verify(recipeRepository).save(recipe);
    }

    @Test
    void leavesImageUnsetWhenNoDishSpecificDatabaseMatchExists() {
        UUID id = UUID.randomUUID();
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName("Chicken stew");
        RecipeTags tags = RecipeTags.builder()
                .protein("chicken")
                .cuisine("Italian")
                .build();
        ImageLibrary sameCuisineOnly = ImageLibrary.builder()
                .imageUrl("https://images.test/italian-dish.jpg")
                .cuisine("Italian")
                .source(ImageSource.BACKFILL)
                .build();
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));
        when(tagExtractor.extract(recipe)).thenReturn(tags);
        when(imageLibraryRepository.findAll()).thenReturn(List.of(sameCuisineOnly));

        service.matchAndAssignImageAsync(id);

        assertThat(recipe.getImage()).isNull();
        verify(recipeRepository, never()).save(any(Recipe.class));
        verify(recipeImageMatchRepository).save(argThat(match ->
                match.getPath() == ImageMatchPath.FALLBACK && match.getChosenImageUrl() == null));
        assertThat(service.assignFromLibrary(recipe)).isFalse();
    }

    private static ImageLibrary libraryImage(
            String url, String protein, String dishType, String ingredients, long timesUsed) {
        return ImageLibrary.builder()
                .imageUrl(url)
                .protein(protein)
                .dishType(dishType)
                .ingredients(ingredients)
                .source(ImageSource.BACKFILL)
                .timesUsed(timesUsed)
                .build();
    }
}
