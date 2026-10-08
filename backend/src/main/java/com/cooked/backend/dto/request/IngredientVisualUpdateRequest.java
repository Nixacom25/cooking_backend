package com.cooked.backend.dto.request;

import com.cooked.backend.entity.IngredientDelivery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Partial edit from the preview drawer: null = unchanged; animation "" = no preset; aliases replace the list. */
public record IngredientVisualUpdateRequest(
        @Size(max = 120) String name,
        @Size(max = 80) String category,
        @Size(max = 80) String collection,
        @Size(max = 40) String animation,
        @Size(max = 30) List<@Size(max = 120) String> aliases,
        @Size(max = 40) String archetype,
        IngredientDelivery delivery,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String mainColor,
        Boolean reviewed,
        Boolean enabled) {
}
