package com.cooked.backend.dto.request;

import com.cooked.backend.entity.IngredientDelivery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * New canonical ingredient. Art: either {@code svg} (upload) or {@code looksLike} (canonical id of a visual
 * whose art is reused and recoloured with {@code mainColor}); without both it starts as Missing Asset.
 * {@code fromUnmatchedId} marks that Not-in-catalog name as resolved.
 */
public record IngredientVisualCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z][a-z0-9]*(_[a-z0-9]+)*", message = "snake_case") String canonicalId,
        @NotBlank @Size(max = 80) String category,
        @Size(max = 80) String collection,
        @Size(max = 40) String animation,
        @Size(max = 30) List<@Size(max = 120) String> aliases,
        @Size(max = 64000) String svg,
        @Size(max = 80) String looksLike,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String mainColor,
        @Size(max = 40) String archetype,
        IngredientDelivery delivery,
        UUID fromUnmatchedId) {
}
