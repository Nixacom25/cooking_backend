package com.cooked.backend.service;

import com.cooked.backend.dto.request.AiRecipeImageReviewRequest;
import com.cooked.backend.dto.response.AiRecipeImageResponse;
import com.cooked.backend.dto.response.AiRecipeImageStatsResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/** Admin review of creator AI recipe photos and the photo library they feed. */
public interface AiRecipeImageReviewService {

    /** Reasons an admin may pick when rejecting a photo. */
    List<String> REJECTION_REASONS = List.of(
            "DOES_NOT_MATCH", "WRONG_INGREDIENTS", "POOR_QUALITY", "UNREALISTIC",
            "BAD_COMPOSITION", "VISUAL_ARTIFACTS", "DUPLICATE", "OTHER");

    /** [filter]: pending | approved | rejected | needs_regeneration | failed | all. */
    Page<AiRecipeImageResponse> list(String filter, int page, int size);

    AiRecipeImageStatsResponse stats(int days);

    /** Adds the photo to the shared image library with structured tags. */
    AiRecipeImageResponse approve(UUID id, AiRecipeImageReviewRequest request, String adminEmail);

    /** Keeps the photo on its recipe but out of the library (removed if it was approved). */
    AiRecipeImageResponse reject(UUID id, AiRecipeImageReviewRequest request, String adminEmail);

    /** Starts a new photo from the same recipe data; returns the new (generating) photo. */
    AiRecipeImageResponse regenerate(UUID id, String adminEmail);
}
