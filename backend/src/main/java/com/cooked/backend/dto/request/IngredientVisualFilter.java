package com.cooked.backend.dto.request;

import com.cooked.backend.entity.IngredientDelivery;
import com.cooked.backend.entity.IngredientVisualStatus;

/** Ingredient Library filters; animation = a preset name or "NONE" for visuals without one. */
public record IngredientVisualFilter(String q, IngredientVisualStatus status, String category, String collection,
                                     String animation, IngredientDelivery delivery) {

    public IngredientVisualFilter withoutStatus() {
        return new IngredientVisualFilter(q, null, category, collection, animation, delivery);
    }

    public IngredientVisualFilter withStatus(IngredientVisualStatus s) {
        return new IngredientVisualFilter(q, s, category, collection, animation, delivery);
    }
}
