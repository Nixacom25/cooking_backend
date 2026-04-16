package com.cooked.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateRecipeRequest {
    @NotBlank(message = "Recipe name is required")
    private String name;

    private String image;
    private Integer cookTime;
    private Integer kcal;
    private Integer servings;
    private String tips;

    @Valid
    private List<IngredientPayload> ingredients;

    private List<String> steps;
    private String sourceUrl;

    // Optional: cookbooks to attach to upon creation
    private List<UUID> cookbookIds;
}
