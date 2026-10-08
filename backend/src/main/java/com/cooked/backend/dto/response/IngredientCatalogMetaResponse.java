package com.cooked.backend.dto.response;

import java.util.List;
import java.util.Map;

/** Choices of the edit/add drawers: presets, categories, art that can be reused, upload rules. */
public record IngredientCatalogMetaResponse(List<String> presets, List<String> categories, List<String> collections,
                                            Map<String, String> suggestedAnimation, List<Reusable> reusable,
                                            int maxSvgBytes, long existingNames) {

    public record Reusable(String canonicalId, String name, String archetype, String mainColor, String svg) {
    }
}
