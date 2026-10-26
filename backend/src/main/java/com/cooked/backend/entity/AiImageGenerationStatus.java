package com.cooked.backend.entity;

/** Where an AI recipe photo generation stands (see {@link RecipeGeneratedImage}). */
public enum AiImageGenerationStatus {
    GENERATING,
    SUCCEEDED,
    /** Every attempt failed: the photo link serves the recipe's previous image instead. */
    FAILED
}
