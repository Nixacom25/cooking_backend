package com.cooked.backend.service;

import com.cooked.backend.service.dto.RecipeImageInput;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes the image prompt for a creator recipe photo from the whole recipe
 * (title, ingredients with quantities, method, cuisine, dish type, finished
 * dish), never from the title alone, so the photo shows that exact dish.
 */
public final class RecipeImagePromptBuilder {

    /** Basics the AI may assume; they don't change what the dish looks like. */
    private static final Set<String> PANTRY = Set.of(
            "salt", "pepper", "black pepper", "water", "oil", "olive oil", "cooking oil", "vegetable oil");
    private static final int MAX_INGREDIENTS = 14;
    private static final int MAX_STEP_CHARS = 220;

    private RecipeImagePromptBuilder() {
    }

    public static String build(RecipeImageInput recipe) {
        StringBuilder p = new StringBuilder();
        p.append("Create a highly realistic, appetizing professional food photograph of ")
                .append(clean(recipe.title()));
        String kind = dishKind(recipe);
        if (!kind.isEmpty()) p.append(", ").append(kind);
        p.append(".\n");

        List<String> visible = visibleIngredients(recipe);
        if (!visible.isEmpty()) {
            p.append("Made with: ").append(String.join(", ", visible)).append(".\n");
        }

        List<String> steps = recipe.steps() == null ? List.of() : recipe.steps();
        if (!steps.isEmpty()) {
            p.append("Cooking method: ").append(shorten(steps.get(0)));
            if (steps.size() > 2) p.append(" ").append(shorten(steps.get(steps.size() / 2)));
            p.append("\n");
            p.append("Finished dish and plating: ").append(shorten(steps.get(steps.size() - 1))).append("\n");
        }

        p.append("The finished dish must visibly reflect the listed ingredients and cooking method. ")
                .append("Do not show ingredients, proteins, garnishes or sides that are not listed. ")
                .append("Natural restaurant-quality plating, believable portion, realistic textures, ")
                .append("soft natural lighting, premium editorial food photography, clean uncluttered background. ")
                .append("Shot at a 45-degree angle, the whole dish centered and fully in frame with space around it, ")
                .append("so it still looks right cropped to a wide banner or a square. ")
                .append("No text, no people, no hands, no logos, no watermarks, no obvious AI artifacts.");
        return p.toString();
    }

    /** "a Mexican tacos dish" → which cuisine / dish type the photo should read as. */
    private static String dishKind(RecipeImageInput recipe) {
        String cuisine = recipe.cuisine() == null ? "" : clean(recipe.cuisine());
        String type = recipe.dishTypes() == null ? "" : recipe.dishTypes().stream()
                .filter(t -> t != null && !t.isBlank()).map(RecipeImagePromptBuilder::clean)
                .limit(2).collect(Collectors.joining(" / "));
        if (cuisine.isEmpty() && type.isEmpty()) return "";
        return ("a " + cuisine + " " + type).replaceAll("\\s+", " ").trim();
    }

    static List<String> visibleIngredients(RecipeImageInput recipe) {
        if (recipe.ingredients() == null) return List.of();
        return recipe.ingredients().stream()
                .filter(i -> !PANTRY.contains(i.name().toLowerCase(Locale.ROOT).trim()))
                .limit(MAX_INGREDIENTS)
                .map(i -> {
                    String qty = i.quantity() == null ? "" : i.quantity().trim();
                    boolean useful = !qty.isEmpty() && !qty.equals("-") && !qty.equals("1");
                    return useful ? clean(i.name()) + " (" + clean(qty) + ")" : clean(i.name());
                })
                .toList();
    }

    private static String shorten(String step) {
        String s = clean(step);
        return s.length() <= MAX_STEP_CHARS ? s : s.substring(0, MAX_STEP_CHARS).replaceAll("\\s+\\S*$", "") + "…";
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
