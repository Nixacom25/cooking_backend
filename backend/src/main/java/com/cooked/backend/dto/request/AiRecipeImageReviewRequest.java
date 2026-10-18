package com.cooked.backend.dto.request;

import com.cooked.backend.dto.response.AiRecipeImageTags;

/**
 * Admin decision on an AI recipe photo: [tags] when approving (missing fields
 * fall back to the suggested tags), [reason] when rejecting.
 */
public record AiRecipeImageReviewRequest(AiRecipeImageTags tags, String reason) {
}
