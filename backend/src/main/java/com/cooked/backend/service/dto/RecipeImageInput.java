package com.cooked.backend.service.dto;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;

import java.util.List;
import java.util.Objects;

/**
 * The structured recipe an AI photo is generated from, stored as JSON on
 * RecipeGeneratedImage so the same photo can be regenerated later.
 */
public record RecipeImageInput(String title, String cuisine, List<String> dishTypes,
                               List<Ingredient> ingredients, List<String> steps) {

    public record Ingredient(String name, String quantity) {
    }

    public static RecipeImageInput from(CreateRecipeRequest recipe) {
        List<IngredientPayload> ingredients = recipe.getIngredients() == null ? List.of() : recipe.getIngredients();
        return new RecipeImageInput(
                recipe.getName(),
                recipe.getCuisine(),
                recipe.getCategories() == null ? List.of() : List.copyOf(recipe.getCategories()),
                ingredients.stream()
                        .filter(Objects::nonNull)
                        .filter(i -> i.getName() != null && !i.getName().isBlank())
                        .map(i -> new Ingredient(i.getName().trim(), i.getQuantity()))
                        .toList(),
                recipe.getSteps() == null ? List.of() : List.copyOf(recipe.getSteps()));
    }

    public List<String> ingredientNames() {
        return ingredients == null ? List.of() : ingredients.stream().map(Ingredient::name).toList();
    }
}
