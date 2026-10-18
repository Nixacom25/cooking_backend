package com.cooked.backend.entity;

public enum ImageSource {
    BACKFILL,
    EDAMAM,
    MANUAL,
    /** AI photo of a creator recipe, approved by an admin (see RecipeGeneratedImage). */
    AI_GENERATED
}
