package com.cooked.backend.entity;

/** Library status, derived from the visual's fields on every save (first matching rule wins). */
public enum IngredientVisualStatus {
    DISABLED,
    MISSING_ASSET,
    MISSING_ANIMATION,
    NEEDS_REVIEW,
    COMPLETE
}
