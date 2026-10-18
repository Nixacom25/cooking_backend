package com.cooked.backend.entity;

/** Admin review of an AI recipe photo. Only APPROVED photos enter the shared image library. */
public enum AiImageApprovalStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    /** Replaced by a regenerated photo (see {@link RecipeGeneratedImage#getRegeneratedFromId()}). */
    NEEDS_REGENERATION
}
