package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One AI recipe photo in the admin review queue. */
public record AiRecipeImageResponse(
        UUID id,
        UUID recipeId,
        String recipeTitle,
        String cuisine,
        List<String> dishTypes,
        /** "chicken breast (2)", in recipe order. */
        List<String> ingredients,
        String imageUrl,
        String prompt,
        String provider,
        String model,
        String quality,
        String generationStatus,
        boolean retried,
        String failureReason,
        Double matchConfidence,
        Integer durationMs,
        Double costUsd,
        UUID creatorId,
        String creatorName,
        String creatorEmail,
        UUID scanId,
        String recipeVersion,
        String approvalStatus,
        String rejectionReason,
        String reviewedBy,
        LocalDateTime reviewedAt,
        UUID regeneratedFromId,
        boolean inLibrary,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        /** Pre-filled library tags for the Approve form. */
        AiRecipeImageTags suggestedTags) {
}
