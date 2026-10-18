package com.cooked.backend.entity;

/** Where an AI recipe photo generation stands (see {@link RecipeGeneratedImage}). */
public enum AiImageGenerationStatus {
    GENERATING,
    SUCCEEDED,
    /** Both attempts failed: the recipe shows the "photo unavailable" placeholder. */
    FAILED
}
