package com.cooked.backend.service;

import com.cooked.backend.service.dto.RecipeImageInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeImagePromptBuilderTest {

    private static final RecipeImageInput PASTA = new RecipeImageInput(
            "Creamy Garlic Chicken Pasta", "Italian", List.of("Pasta"),
            List.of(new RecipeImageInput.Ingredient("fettuccine", "250 g"),
                    new RecipeImageInput.Ingredient("chicken breast", "2"),
                    new RecipeImageInput.Ingredient("parmesan", "-"),
                    new RecipeImageInput.Ingredient("salt", "1 pinch"),
                    new RecipeImageInput.Ingredient("cherry tomatoes", "1")),
            List.of("Slice the chicken and season it.", "Sear the chicken, then simmer garlic in cream.",
                    "Toss the fettuccine in the sauce and top with parsley."));

    @Test
    void build_describesTheWholeRecipeNotJustTheTitle() {
        String prompt = RecipeImagePromptBuilder.build(PASTA);

        assertThat(prompt)
                .startsWith("Create a highly realistic, appetizing professional food photograph of Creamy Garlic Chicken Pasta, a Italian Pasta.")
                .contains("Made with: fettuccine (250 g), chicken breast (2), parmesan, cherry tomatoes.")
                .contains("Cooking method: Slice the chicken")
                .contains("Finished dish and plating: Toss the fettuccine in the sauce and top with parsley.")
                .contains("Do not show ingredients, proteins, garnishes or sides that are not listed.")
                .contains("No text, no people");
    }

    @Test
    void visibleIngredients_leavesOutPantryBasics() {
        assertThat(RecipeImagePromptBuilder.visibleIngredients(PASTA)).noneMatch(i -> i.startsWith("salt"));
    }

    @Test
    void build_worksWithOnlyATitle() {
        String prompt = RecipeImagePromptBuilder.build(new RecipeImageInput("Tacos", null, null, null, null));
        assertThat(prompt).startsWith("Create a highly realistic, appetizing professional food photograph of Tacos.\n")
                .doesNotContain("Made with").doesNotContain("Cooking method");
    }
}
