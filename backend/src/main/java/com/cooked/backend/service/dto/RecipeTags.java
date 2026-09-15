package com.cooked.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured tags describing a recipe's dish for image matching: dish type,
 * protein, cuisine, main ingredients, cooking style. Same shape as the
 * columns on {@link com.cooked.backend.entity.ImageLibrary}, so a recipe's
 * tags can be scored directly against a library row.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RecipeTags {
    private String cuisine;
    private String protein;
    private String dishType;
    private String cookingStyle;
    @Builder.Default
    private List<String> mainIngredients = List.of();
    @Builder.Default
    private List<String> freeTextKeywords = List.of();

    public String describe() {
        return "cuisine=" + (cuisine == null ? "" : cuisine)
                + "; protein=" + (protein == null ? "" : protein)
                + "; dishType=" + (dishType == null ? "" : dishType)
                + "; cookingStyle=" + (cookingStyle == null ? "" : cookingStyle)
                + "; ingredients=" + String.join(",", mainIngredients)
                + "; keywords=" + String.join(",", freeTextKeywords);
    }

    public String searchQuery() {
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();
        if (protein != null) parts.add(protein);
        if (dishType != null) parts.add(dishType);
        if (cookingStyle != null) parts.add(cookingStyle);
        if (cuisine != null) parts.add(cuisine);
        parts.addAll(mainIngredients);
        if (parts.isEmpty()) parts.addAll(freeTextKeywords);
        return String.join(" ", parts);
    }
}
