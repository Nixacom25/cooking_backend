package com.cooked.backend.service;

import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeIngredient;
import com.cooked.backend.service.dto.RecipeTags;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Turns a {@link Recipe} into the structured tags used for image matching
 * (dish type, protein, cuisine, main ingredients, cooking style), e.g.
 * "Smoky Garlic Chicken Rice Bowl" -> chicken + rice bowl + grilled + garlic.
 */
@Component
public class RecipeTagExtractor {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "with", "for", "recipe", "style", "easy", "quick",
            "best", "homemade", "classic", "simple", "delicious", "spicy",
            "le", "la", "les", "et", "avec", "pour", "recette", "facile"
    );

    private static final List<String> PROTEIN_KEYWORDS = List.of(
            "chicken", "beef", "pork", "turkey", "lamb", "salmon", "tuna",
            "shrimp", "fish", "tofu", "egg", "eggs", "bacon", "sausage",
            "beans", "lentil", "lentils", "chickpea", "chickpeas"
    );

    private static final List<String> COOKING_STYLE_KEYWORDS = List.of(
            "grilled", "baked", "roasted", "fried", "stir-fried", "stir fried",
            "steamed", "boiled", "slow-cooked", "slow cooked", "air-fried",
            "air fried", "sauteed", "sautéed", "braised", "smoked", "bbq",
            "barbecue", "poached", "raw", "one-pot", "one pot"
    );

    private static final Set<String> GENERIC_INGREDIENTS = Set.of(
            "salt", "pepper", "black pepper", "water", "oil", "olive oil",
            "sugar", "butter"
    );

    public RecipeTags extract(Recipe recipe) {
        String name = recipe.getName() == null ? "" : recipe.getName().toLowerCase(Locale.ROOT);

        List<String> ingredientNames = new ArrayList<>();
        if (recipe.getRecipeIngredients() != null) {
            for (RecipeIngredient ri : recipe.getRecipeIngredients()) {
                if (ri.getIngredient() == null || ri.getIngredient().getName() == null) continue;
                String ing = ri.getIngredient().getName().toLowerCase(Locale.ROOT).trim();
                if (!ing.isEmpty() && !GENERIC_INGREDIENTS.contains(ing)) {
                    ingredientNames.add(ing);
                }
            }
        }

        String protein = findFirstKeyword(name, ingredientNames, PROTEIN_KEYWORDS);
        String cookingStyle = findFirstKeyword(name, List.of(), COOKING_STYLE_KEYWORDS);

        String dishType = null;
        if (recipe.getCategories() != null && !recipe.getCategories().isEmpty()) {
            dishType = recipe.getCategories().iterator().next().getName();
        }

        String cuisine = recipe.getCuisine() != null ? recipe.getCuisine().getName() : null;

        List<String> mainIngredients = ingredientNames.stream()
                .filter(ing -> !ing.equals(protein))
                .limit(2)
                .toList();

        List<String> freeTextKeywords = extractKeywords(name);

        return RecipeTags.builder()
                .cuisine(cuisine)
                .protein(protein)
                .dishType(dishType)
                .cookingStyle(cookingStyle)
                .mainIngredients(mainIngredients)
                .freeTextKeywords(freeTextKeywords)
                .build();
    }

    /** Drops the cooking style for a broadened retry against the same recipe. */
    public RecipeTags broaden(RecipeTags tags) {
        return tags.toBuilder().cookingStyle(null).build();
    }

    private String findFirstKeyword(String name, List<String> ingredientNames, List<String> keywords) {
        for (String kw : keywords) {
            if (name.contains(kw)) return kw;
        }
        for (String ing : ingredientNames) {
            for (String kw : keywords) {
                if (ing.contains(kw)) return kw;
            }
        }
        return null;
    }

    private List<String> extractKeywords(String name) {
        String[] words = name.split("\\W+");
        List<String> keywords = new ArrayList<>();
        for (String w : words) {
            if (w.length() > 3 && !STOP_WORDS.contains(w)) {
                keywords.add(w);
            }
        }
        keywords.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return keywords;
    }
}
