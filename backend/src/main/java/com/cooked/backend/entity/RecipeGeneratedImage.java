package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One AI-generated recipe photo (creators only, see CreatorRecipeImageService):
 * the exact prompt, model and outcome, the recipe data it was made from, and its
 * admin review. Scan recipes are only saved when the creator keeps one, so
 * {@link #recipeId} is filled in later, when a recipe is saved with this photo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "recipe_generated_images", indexes = {
        @Index(name = "idx_recipe_generated_images_approval_created", columnList = "approvalStatus, createdAt"),
        @Index(name = "idx_recipe_generated_images_url", columnList = "generatedImageUrl")
})
public class RecipeGeneratedImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Saved recipe using this photo (null until the creator saves the scan recipe). */
    private UUID recipeId;

    @Column(nullable = false, length = 255)
    private String recipeTitle;

    /** Short hash of {@link #recipeSnapshot}: which version of the recipe the prompt describes. */
    @Column(length = 16)
    private String recipeVersion;

    /** Structured recipe the photo was made from (JSON), reused to regenerate it. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipeSnapshot;

    @Column(columnDefinition = "TEXT")
    private String generatedImageUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String imagePrompt;

    @Column(nullable = false, length = 40)
    private String imageProvider;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(length = 20)
    private String quality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiImageGenerationStatus generationStatus;

    /** The first attempt failed and a second one was made. */
    private boolean retried;

    @Column(length = 500)
    private String failureReason;

    /** Vision check of the photo against the recipe (0-1), null when it could not run. */
    private Double matchConfidence;

    private Integer durationMs;

    /** Estimated provider cost of every attempt that produced an image. */
    private Double costUsd;

    private UUID creatorUserId;

    /** Groups the photos of one scan. */
    private UUID scanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private AiImageApprovalStatus approvalStatus = AiImageApprovalStatus.PENDING_REVIEW;

    @Column(length = 60)
    private String rejectionReason;

    @Column(length = 160)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    /** Photo this one was regenerated from. */
    private UUID regeneratedFromId;

    /** Library row created when the photo was approved. */
    private UUID imageLibraryId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
