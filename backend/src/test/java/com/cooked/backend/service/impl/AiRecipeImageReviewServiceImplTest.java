package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeImageReviewRequest;
import com.cooked.backend.dto.response.AiRecipeImageStatsResponse;
import com.cooked.backend.dto.response.AiRecipeImageTags;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.ImageLibraryRepository;
import com.cooked.backend.repository.RecipeGeneratedImageRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.CreatorRecipeImageService;
import com.cooked.backend.service.RecipeTagExtractor;
import com.cooked.backend.service.dto.RecipeImageInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiRecipeImageReviewServiceImplTest {

    private final RecipeGeneratedImageRepository images = mock(RecipeGeneratedImageRepository.class);
    private final ImageLibraryRepository library = mock(ImageLibraryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CreatorRecipeImageService creatorImages = mock(CreatorRecipeImageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiRecipeImageReviewServiceImpl service;
    private RecipeGeneratedImage row;

    @BeforeEach
    void setUp() throws Exception {
        service = new AiRecipeImageReviewServiceImpl(images, library, users, creatorImages, new RecipeTagExtractor(), objectMapper);
        RecipeImageInput input = new RecipeImageInput("Grilled Salmon Rice Bowl", "Japanese", List.of("Bowl"),
                List.of(new RecipeImageInput.Ingredient("salmon", "200 g"),
                        new RecipeImageInput.Ingredient("rice", "1 cup"),
                        new RecipeImageInput.Ingredient("avocado", "1")),
                List.of("Grill the salmon.", "Serve over rice."));
        row = RecipeGeneratedImage.builder()
                .id(UUID.randomUUID())
                .recipeTitle(input.title())
                .recipeSnapshot(objectMapper.writeValueAsString(input))
                .generatedImageUrl("https://res.cloudinary.com/x/salmon.jpg")
                .imagePrompt("prompt").imageProvider("openai").model("gpt-image-1")
                .generationStatus(AiImageGenerationStatus.SUCCEEDED)
                .build();
        when(images.findById(row.getId())).thenReturn(Optional.of(row));
        when(images.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(library.save(any())).thenAnswer(inv -> {
            ImageLibrary e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void approve_addsThePhotoToTheLibraryWithStructuredTags() {
        AiRecipeImageTags given = new AiRecipeImageTags(null, null, null, null, "Dinner", null,
                List.of("Gluten-free"), null, 5);

        var response = service.approve(row.getId(), new AiRecipeImageReviewRequest(given, null), "admin@cooked.test");

        ArgumentCaptor<ImageLibrary> entry = ArgumentCaptor.forClass(ImageLibrary.class);
        verify(library).save(entry.capture());
        ImageLibrary saved = entry.getValue();
        assertThat(saved.getSource()).isEqualTo(ImageSource.AI_GENERATED);
        assertThat(saved.getImageUrl()).isEqualTo("https://res.cloudinary.com/x/salmon.jpg");
        assertThat(saved.getProtein()).isEqualTo("salmon");
        assertThat(saved.getDishType()).isEqualTo("bowl");
        assertThat(saved.getCuisine()).isEqualTo("japanese");
        assertThat(saved.getCookingStyle()).isEqualTo("grilled");
        assertThat(saved.getMealType()).isEqualTo("dinner");
        assertThat(saved.getDietaryTags()).isEqualTo("gluten-free");
        assertThat(saved.getQualityScore()).isEqualTo(5);
        assertThat(saved.getRecipeTitle()).isEqualTo("Grilled Salmon Rice Bowl");
        assertThat(response.approvalStatus()).isEqualTo("APPROVED");
        assertThat(response.inLibrary()).isTrue();
        assertThat(row.getReviewedBy()).isEqualTo("admin@cooked.test");
    }

    @Test
    void approve_refusesAFailedPhoto() {
        row.setGenerationStatus(AiImageGenerationStatus.FAILED);
        assertThatThrownBy(() -> service.approve(row.getId(), null, "admin@cooked.test"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(library);
    }

    @Test
    void reject_needsAReasonAndRemovesAnApprovedPhotoFromTheLibrary() {
        UUID libraryId = UUID.randomUUID();
        row.setImageLibraryId(libraryId);
        row.setApprovalStatus(AiImageApprovalStatus.APPROVED);

        assertThatThrownBy(() -> service.reject(row.getId(), new AiRecipeImageReviewRequest(null, "meh"), "a@b.c"))
                .isInstanceOf(BadRequestException.class);

        var response = service.reject(row.getId(), new AiRecipeImageReviewRequest(null, "wrong_ingredients"), "a@b.c");

        verify(library).deleteById(libraryId);
        assertThat(response.approvalStatus()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("WRONG_INGREDIENTS");
        assertThat(response.inLibrary()).isFalse();
    }

    @Test
    void stats_turnsCountsIntoEventsRatesAndCost() {
        when(images.statsByModel(any())).thenReturn(List.<Object[]>of(
                new Object[]{"gpt-image-1", 10L, 8L, 2L, 3L, 6L, 2L, 1L, 6L, 30000.0, 0.5}));
        when(images.rejectionReasons(any())).thenReturn(List.<Object[]>of(new Object[]{"POOR_QUALITY", 2L}));
        when(images.countByApprovalStatus(AiImageApprovalStatus.PENDING_REVIEW)).thenReturn(4L);

        AiRecipeImageStatsResponse stats = service.stats(30);

        assertThat(stats.events()).containsEntry("recipe_image_generation_started", 10L)
                .containsEntry("recipe_image_generation_completed", 8L)
                .containsEntry("recipe_image_generation_failed", 2L)
                .containsEntry("recipe_image_generation_retried", 3L)
                .containsEntry("recipe_image_admin_approved", 6L)
                .containsEntry("recipe_image_admin_rejected", 2L)
                .containsEntry("recipe_image_regenerated", 1L)
                .containsEntry("recipe_image_added_to_database", 6L);
        assertThat(stats.pendingReview()).isEqualTo(4);
        assertThat(stats.avgGenerationMs()).isEqualTo(30000.0);
        assertThat(stats.totalCostUsd()).isEqualTo(0.5);
        assertThat(stats.costPerImage()).isEqualTo(0.05);
        assertThat(stats.failureRate()).isEqualTo(20.0);
        assertThat(stats.retryRate()).isEqualTo(30.0);
        assertThat(stats.approvalRate()).isEqualTo(75.0);
        assertThat(stats.rejectionRate()).isEqualTo(25.0);
        assertThat(stats.rejectionReasons()).containsEntry("POOR_QUALITY", 2L);
        assertThat(stats.models()).singleElement().satisfies(m -> assertThat(m.approvalRate()).isEqualTo(75.0));
    }
}
