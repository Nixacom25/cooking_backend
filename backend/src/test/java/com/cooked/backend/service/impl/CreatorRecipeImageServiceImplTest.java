package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.entity.AiImageApprovalStatus;
import com.cooked.backend.entity.AiImageGenerationStatus;
import com.cooked.backend.entity.RecipeGeneratedImage;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.RecipeGeneratedImageRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiImageGenerator;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.VisionImageRankerService;
import com.cooked.backend.service.dto.VisionRankResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreatorRecipeImageServiceImplTest {

    private static final String PLACEHOLDER = "https://link.cookedapp.com/share/photo-unavailable.jpg";

    private final RecipeGeneratedImageRepository images = mock(RecipeGeneratedImageRepository.class);
    private final RecipeRepository recipes = mock(RecipeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AiImageGenerator generator = mock(AiImageGenerator.class);
    private final CloudinaryService cloudinary = mock(CloudinaryService.class);
    private final VisionImageRankerService vision = mock(VisionImageRankerService.class);
    private final List<RecipeGeneratedImage> saved = new ArrayList<>();
    private CreatorRecipeImageServiceImpl service;

    @BeforeEach
    void setUp() throws IOException {
        when(images.save(any())).thenAnswer(inv -> {
            RecipeGeneratedImage row = inv.getArgument(0);
            if (row.getId() == null) row.setId(UUID.randomUUID());
            if (!saved.contains(row)) saved.add(row);
            return row;
        });
        when(generator.provider()).thenReturn("openai");
        when(generator.model()).thenReturn("gpt-image-1");
        when(generator.quality()).thenReturn("medium");
        when(generator.generate(anyString())).thenReturn(new AiImageGenerator.GeneratedImage(new byte[]{1, 2, 3}, "gpt-image-1", 0.04));
        when(cloudinary.uploadBytes(any(), eq("ai-recipes"))).thenReturn("https://res.cloudinary.com/x/ai.jpg");
        when(vision.rankCandidates(anyString(), anyList()))
                .thenReturn(VisionRankResult.builder().bestIndex(0).confidence(0.9).reason("ok").build());
        service = new CreatorRecipeImageServiceImpl(images, recipes, users, generator, cloudinary, vision,
                new ObjectMapper(), true, 30, 0.5, 4, "https://link.cookedapp.com");
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private User user(Role role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(role.name().toLowerCase() + "@cooked.test");
        u.setRole(role);
        when(users.findByEmail(u.getEmail())).thenReturn(Optional.of(u));
        return u;
    }

    private static CreateRecipeRequest recipe(String name) {
        CreateRecipeRequest r = new CreateRecipeRequest();
        r.setName(name);
        r.setImage("https://images.unsplash.com/random");
        IngredientPayload chicken = new IngredientPayload();
        chicken.setName("chicken");
        chicken.setQuantity("2");
        r.setIngredients(List.of(chicken));
        r.setSteps(List.of("Grill the chicken.", "Serve in tortillas."));
        return r;
    }

    @Test
    void attachImages_doesNothingForNonCreators() throws IOException {
        for (Role role : List.of(Role.CLIENT, Role.ADMIN, Role.EDITOR)) {
            CreateRecipeRequest r = recipe("Chicken Tacos");
            service.attachImages(List.of(r), user(role).getEmail());
            assertThat(r.getImage()).isEqualTo("https://images.unsplash.com/random");
        }
        verify(generator, never()).generate(anyString());
        verifyNoInteractions(cloudinary);
        assertThat(saved).isEmpty();
    }

    @Test
    void attachImages_givesEachCreatorRecipeItsOwnAiPhoto() {
        User creator = user(Role.CREATOR);
        CreateRecipeRequest tacos = recipe("Chicken Tacos");
        CreateRecipeRequest bowl = recipe("Chicken Bowl");

        service.attachImages(List.of(tacos, bowl), creator.getEmail());

        assertThat(tacos.getImage()).isEqualTo("https://res.cloudinary.com/x/ai.jpg");
        assertThat(bowl.getImage()).isEqualTo("https://res.cloudinary.com/x/ai.jpg");
        assertThat(saved).hasSize(2).allSatisfy(row -> {
            assertThat(row.getGenerationStatus()).isEqualTo(AiImageGenerationStatus.SUCCEEDED);
            assertThat(row.getApprovalStatus()).isEqualTo(AiImageApprovalStatus.PENDING_REVIEW);
            assertThat(row.getCreatorUserId()).isEqualTo(creator.getId());
            assertThat(row.getImagePrompt()).contains("chicken (2)");
            assertThat(row.getCostUsd()).isEqualTo(0.04);
            assertThat(row.getModel()).isEqualTo("gpt-image-1");
            assertThat(row.isRetried()).isFalse();
        });
        assertThat(saved.get(0).getScanId()).isNotNull().isEqualTo(saved.get(1).getScanId());
    }

    @Test
    void attachImages_retriesOnceThenFallsBackToThePlaceholder() throws IOException {
        when(generator.generate(anyString())).thenThrow(new IOException("provider down"));
        CreateRecipeRequest tacos = recipe("Chicken Tacos");

        service.attachImages(List.of(tacos), user(Role.CREATOR).getEmail());

        assertThat(tacos.getImage()).isEqualTo(PLACEHOLDER);
        assertThat(tacos.getName()).isEqualTo("Chicken Tacos");
        verify(generator, times(2)).generate(anyString());
        RecipeGeneratedImage row = saved.get(0);
        assertThat(row.getGenerationStatus()).isEqualTo(AiImageGenerationStatus.FAILED);
        assertThat(row.isRetried()).isTrue();
        assertThat(row.getFailureReason()).contains("provider down");
    }

    @Test
    void attachImages_treatsAPhotoThatDoesNotMatchTheRecipeAsAFailure() throws IOException {
        when(vision.rankCandidates(anyString(), anyList()))
                .thenReturn(VisionRankResult.builder().bestIndex(-1).confidence(0.8).reason("chicken alfredo").build())
                .thenReturn(VisionRankResult.builder().bestIndex(0).confidence(0.85).reason("tacos").build());
        CreateRecipeRequest tacos = recipe("Chicken Tacos");

        service.attachImages(List.of(tacos), user(Role.CREATOR).getEmail());

        assertThat(tacos.getImage()).isEqualTo("https://res.cloudinary.com/x/ai.jpg");
        RecipeGeneratedImage row = saved.get(0);
        assertThat(row.isRetried()).isTrue();
        assertThat(row.getMatchConfidence()).isEqualTo(0.85);
        assertThat(row.getCostUsd()).isEqualTo(0.08);
        verify(generator, times(2)).generate(anyString());
    }

    @Test
    void attachImages_recordsTheModelThatActuallyMadeThePhoto() throws IOException {
        when(generator.generate(anyString())).thenReturn(new AiImageGenerator.GeneratedImage(new byte[]{1}, "dall-e-3", 0.04));
        CreateRecipeRequest tacos = recipe("Chicken Tacos");

        service.attachImages(List.of(tacos), user(Role.CREATOR).getEmail());

        assertThat(saved.get(0).getModel()).isEqualTo("dall-e-3");
    }

    @Test
    void attachImages_isOffWhenTheFeatureIsDisabled() {
        service.shutdown();
        service = new CreatorRecipeImageServiceImpl(images, recipes, users, generator, cloudinary, vision,
                new ObjectMapper(), false, 30, 0.5, 4, "https://link.cookedapp.com");
        CreateRecipeRequest tacos = recipe("Chicken Tacos");

        service.attachImages(List.of(tacos), user(Role.CREATOR).getEmail());

        assertThat(tacos.getImage()).isEqualTo("https://images.unsplash.com/random");
        assertThat(saved).isEmpty();
    }

    @Test
    void linkSavedRecipe_attachesThePhotoToTheSavedRecipe() {
        RecipeGeneratedImage row = RecipeGeneratedImage.builder().id(UUID.randomUUID()).build();
        when(images.findFirstByGeneratedImageUrlAndRecipeIdIsNull("https://res.cloudinary.com/x/ai.jpg"))
                .thenReturn(Optional.of(row));
        UUID recipeId = UUID.randomUUID();

        service.linkSavedRecipe(recipeId, "https://res.cloudinary.com/x/ai.jpg");
        service.linkSavedRecipe(UUID.randomUUID(), PLACEHOLDER);

        assertThat(row.getRecipeId()).isEqualTo(recipeId);
        verify(images, never()).findFirstByGeneratedImageUrlAndRecipeIdIsNull(PLACEHOLDER);
    }
}
