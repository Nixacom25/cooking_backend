package com.cooked.backend.dto.response;

import com.cooked.backend.entity.IngredientDelivery;
import com.cooked.backend.entity.IngredientVisualStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One row of the Ingredient Library (svg included so the table can draw the art). */
public record IngredientVisualResponse(UUID id, String canonicalId, String name, String category, String collection,
                                       List<String> aliases, String animation, String svg, String svgFile,
                                       Integer svgBytes, String svgSize, String svgHash, String archetype,
                                       IngredientDelivery delivery, String mainColor, boolean enabled, boolean reviewed,
                                       IngredientVisualStatus status, LocalDateTime updatedAt, String updatedBy) {
}
