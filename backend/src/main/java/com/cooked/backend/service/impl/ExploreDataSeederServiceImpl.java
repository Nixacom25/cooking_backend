package com.cooked.backend.service.impl;

import com.cooked.backend.entity.*;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.ExploreDataSeederService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExploreDataSeederServiceImpl implements ExploreDataSeederService {

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeCategoryRepository recipeCategoryRepository;

    private static final Map<String, String> CATEGORY_IMAGES = Map.ofEntries(
        Map.entry("High Protein Picks", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825794/ai-recipe-app/taxonomy/gjndqlconjidynpzgljm.png"),
        Map.entry("Easy Desserts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825799/ai-recipe-app/taxonomy/t8smxtmx8fo5dgu6nq09.png"),
        Map.entry("30-Minute Meals", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825804/ai-recipe-app/taxonomy/nllnev1tt8sbyhzblxnt.png"),
        Map.entry("Healthy Breakfasts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825810/ai-recipe-app/taxonomy/chip5aztermtfu1e1rn7.jpg"),
        Map.entry("Plant-Based Essentials", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825816/ai-recipe-app/taxonomy/uyjylmhr9lv0m6kznfnv.png"),
        Map.entry("Low-Carb Meals", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825822/ai-recipe-app/taxonomy/rcnlugjoz4wgha3wbip1.png"),
        Map.entry("Bowls & Salads", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825828/ai-recipe-app/taxonomy/pfftssqpcneggog8skvr.jpg"),
        Map.entry("Desserts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825832/ai-recipe-app/taxonomy/jsf51txzntu3ouv8lecr.jpg"),
        Map.entry("Pasta & Noodles", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825833/ai-recipe-app/taxonomy/zl9pii59boskpsazbsf9.jpg"),
        Map.entry("Pizza & Flatbreads", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825835/ai-recipe-app/taxonomy/d9g359mpsmvqvdkbah7u.jpg"),
        Map.entry("Curries & Stews", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825839/ai-recipe-app/taxonomy/zkaveyb5ror0ogsxa2a3.jpg"),
        Map.entry("Handheld / Street Food", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825849/ai-recipe-app/taxonomy/dbnakseazqptrnppmxh4.jpg"),
        Map.entry("Soups", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825857/ai-recipe-app/taxonomy/lxx4ebaywhubtnt56s7f.jpg"),
        Map.entry("Rice & Grain", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825868/ai-recipe-app/taxonomy/cnbaetrmnteyizx4wt9g.jpg"),
        Map.entry("Stir Fry", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825877/ai-recipe-app/taxonomy/uxszdotoyanj9raincra.jpg")
    );

    private static final Map<String, String> CUISINE_IMAGES = Map.ofEntries(
        Map.entry("Italy", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825879/ai-recipe-app/taxonomy/fgke47uvihcznhh26xyw.png"),
        Map.entry("Mexico", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825881/ai-recipe-app/taxonomy/v8ompn1xfg2xerin57ea.png"),
        Map.entry("China", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825883/ai-recipe-app/taxonomy/t0dzizwahawi5lrdqnrx.png"),
        Map.entry("Japan", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825885/ai-recipe-app/taxonomy/rkaaoetvlogvojyrbbal.png"),
        Map.entry("Thailand", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825887/ai-recipe-app/taxonomy/kpf7vnohqamnafqlj9xn.png"),
        Map.entry("India", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825896/ai-recipe-app/taxonomy/fhzwu5howhuiui6mdxl9.jpg"),
        Map.entry("South Korea", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825899/ai-recipe-app/taxonomy/az5qhmzv8ktvmybrgodx.jpg"),
        Map.entry("Mediterranean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825909/ai-recipe-app/taxonomy/tv6ffhbuwenbqjycdf2m.png"),
        Map.entry("Middle East", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825913/ai-recipe-app/taxonomy/kjclia9clnpalj5hmrr8.png"),
        Map.entry("France", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825928/ai-recipe-app/taxonomy/sqhgsctwb0ywicd4sivq.png"),
        Map.entry("Greece", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825941/ai-recipe-app/taxonomy/gtbhwfmco1mqo7j5n10q.png"),
        Map.entry("Caribbean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825954/ai-recipe-app/taxonomy/mvkpcxrsmpt7rtjxkzrw.png"),
        Map.entry("West Africa", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825961/ai-recipe-app/taxonomy/pvqffpmrz1l2bjuoaabh.png"),
        Map.entry("American", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825964/ai-recipe-app/taxonomy/edm1eoulgyyj6ajo9sfx.jpg"),
        Map.entry("Senegalese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825968/ai-recipe-app/taxonomy/qtghqgyehufyrqvohxcy.jpg"),
        Map.entry("Vietnamese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825974/ai-recipe-app/taxonomy/ea3iepmpd92dykzekamg.jpg"),
        Map.entry("Moroccan", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825987/ai-recipe-app/taxonomy/cfn9mjfjtxbz6c0l4vsi.jpg"),
        Map.entry("Asian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825996/ai-recipe-app/taxonomy/fsfudpdnqvfqyqtwpwhj.jpg"),
        Map.entry("Brazilian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778826007/ai-recipe-app/taxonomy/rfdrinxhadmq6lsaj6uw.jpg"),
        Map.entry("British", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778826016/ai-recipe-app/taxonomy/mr6jkzdtemoulb4p436p.jpg")
    );

    @Override
    @Transactional
    public void seedExploreData() {
        log.info("Checking for Explore Data updates in explore_recipes.json...");

        User systemUser = userRepository.findByEmail("explore@cooked.com")
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setFirstname("Cooked");
                    newUser.setLastname("Official");
                    newUser.setEmail("explore@cooked.com");
                    newUser.setPassword("system_no_login");
                    newUser.setRole(Role.ADMIN);
                    newUser.setStatus(Status.ACTIVE);
                    newUser.setProvider(Provider.LOCAL);
                    newUser.setResendCount(0);
                    return userRepository.save(newUser);
                });

        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getResourceAsStream("/explore_recipes.json");
            if (is != null) {
                List<Map<String, Object>> recipesData = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
                log.info("Found {} recipes in explore_recipes.json. Starting seed...", recipesData.size());
                
                for (Map<String, Object> data : recipesData) {
                    try {
                        String name = (String) data.get("name");
                        String cuisineName = (String) data.get("cuisine");
                        String categoryName = (String) data.get("category");

                        Recipe recipe = recipeRepository.findByUserIdAndName(systemUser.getId(), name)
                                .orElseGet(() -> Recipe.builder()
                                        .user(systemUser)
                                        .name(name)
                                        .origin(RecipeOrigin.EXPLORE)
                                        .isPublic(true)
                                        .build());

                        recipe.setCuisine(getOrCreateCategory(cuisineName, CategoryType.CUISINE));
                        recipe.setCategory(getOrCreateCategory(categoryName, CategoryType.CATEGORY));
                        recipe.setImage((String) data.get("image"));
                        recipe.setPrepTime(data.get("prepTime") != null ? (Integer) data.get("prepTime") : 0);
                        recipe.setCookTime(data.get("cookTime") != null ? (Integer) data.get("cookTime") : 0);
                        recipe.setKcal(data.get("kcal") != null ? (Integer) data.get("kcal") : 0);
                        recipe.setServings(data.get("servings") != null ? (Integer) data.get("servings") : 2);
                        recipe.setTips((String) data.get("tips"));
                        recipe.setSourceUrl((String) data.get("sourceUrl"));
                        
                        Object stepsObj = data.get("steps");
                        if (stepsObj instanceof List) {
                            recipe.setSteps(new ArrayList<>((List<String>) stepsObj));
                        }
                        
                        Object equipmentObj = data.get("equipment");
                        if (equipmentObj instanceof List) {
                            recipe.setEquipment(new ArrayList<>((List<String>) equipmentObj));
                        }

                        Recipe saved = recipeRepository.save(recipe);
                        
                        Object ingredientsObj = data.get("ingredients");
                        if (ingredientsObj instanceof List) {
                            updateIngredients(saved, (List<Map<String, String>>) ingredientsObj);
                        }
                    } catch (Exception e) {
                        log.error("Failed to create recipe: {}", data.get("name"), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to seed explore data from explore.json", e);
        }
    }

    private RecipeCategory getOrCreateCategory(String name, CategoryType type) {
        if (name == null || name.isBlank()) return null;
        
        // Normalize name based on explore_recipes.json and existing DB variations
        String normalizedName = name.trim();
        if (type == CategoryType.CATEGORY) {
            if (normalizedName.equalsIgnoreCase("Breakfast")) normalizedName = "Healthy Breakfasts";
            if (normalizedName.equalsIgnoreCase("Handheld Street Food")) normalizedName = "Handheld / Street Food";
            if (normalizedName.equalsIgnoreCase("Rice & Grain Dishes")) normalizedName = "Rice & Grain";
            if (normalizedName.equalsIgnoreCase("Stir Fry Sauté Wok")) normalizedName = "Stir Fry";
            if (normalizedName.equalsIgnoreCase("High Protein")) normalizedName = "High Protein Picks";
            if (normalizedName.equalsIgnoreCase("Protein Plates")) normalizedName = "High Protein Picks";
            if (normalizedName.equalsIgnoreCase("Sides & Snacks")) normalizedName = "Handheld / Street Food";
            if (normalizedName.equalsIgnoreCase("Lunch")) normalizedName = "30-Minute Meals";
            if (normalizedName.equalsIgnoreCase("Quick Lunches")) normalizedName = "30-Minute Meals";
            if (normalizedName.equalsIgnoreCase("Vegan Essentials")) normalizedName = "Plant-Based Essentials";
            if (normalizedName.equalsIgnoreCase("Comfort Food")) normalizedName = "Pizza & Flatbreads";
            if (normalizedName.equalsIgnoreCase("Meal Prep Favorites")) normalizedName = "Healthy Breakfasts";
        } else if (type == CategoryType.CUISINE) {
            if (normalizedName.equalsIgnoreCase("Italian")) normalizedName = "Italy";
            if (normalizedName.equalsIgnoreCase("Mexican")) normalizedName = "Mexico";
            if (normalizedName.equalsIgnoreCase("Japanese")) normalizedName = "Japan";
            if (normalizedName.equalsIgnoreCase("French")) normalizedName = "France";
            if (normalizedName.equalsIgnoreCase("Thai")) normalizedName = "Thailand";
            if (normalizedName.equalsIgnoreCase("Chinese")) normalizedName = "China";
            if (normalizedName.equalsIgnoreCase("Indian")) normalizedName = "India";
            if (normalizedName.equalsIgnoreCase("Korean")) normalizedName = "South Korea";
            if (normalizedName.equalsIgnoreCase("Middle Eastern")) normalizedName = "Middle East";
            if (normalizedName.equalsIgnoreCase("West African")) normalizedName = "West Africa";
            if (normalizedName.equalsIgnoreCase("North American")) normalizedName = "American";
            if (normalizedName.equalsIgnoreCase("Japanese Fusion")) normalizedName = "Japan";
            if (normalizedName.equalsIgnoreCase("Thai/Chinese")) normalizedName = "Thailand";
            if (normalizedName.equalsIgnoreCase("South East Asian")) normalizedName = "Thailand";
            if (normalizedName.equalsIgnoreCase("Taiwanese")) normalizedName = "China";
            if (normalizedName.equalsIgnoreCase("Argentine")) normalizedName = "Brazilian";
            if (normalizedName.equalsIgnoreCase("Belgian")) normalizedName = "France";
            if (normalizedName.equalsIgnoreCase("European")) normalizedName = "France";
            if (normalizedName.equalsIgnoreCase("Hawaiian")) normalizedName = "American";
        }
        
        final String finalName = normalizedName;
        return recipeCategoryRepository.findByNameAndType(finalName, type)
                .orElseGet(() -> {
                    String image = (type == CategoryType.CUISINE) ? CUISINE_IMAGES.get(finalName) : CATEGORY_IMAGES.get(finalName);
                    return recipeCategoryRepository.save(RecipeCategory.builder()
                            .name(finalName)
                            .type(type)
                            .image(image)
                            .build());
                });
    }

    private void updateIngredients(Recipe recipe, List<Map<String, String>> ingredients) {
        if (recipe.getRecipeIngredients() == null) {
            recipe.setRecipeIngredients(new HashSet<>());
        } else {
            recipe.getRecipeIngredients().clear();
        }

        for (Map<String, String> ingData : ingredients) {
            String ingName = ingData.get("name");
            String quantity = ingData.get("quantity") != null ? ingData.get("quantity") : "1 unit";
            String icon = ingData.get("icon") != null ? ingData.get("icon") : "🍲";

            Ingredient ingredient = ingredientRepository.findByName(ingName.toLowerCase().trim())
                    .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                            .name(ingName.toLowerCase().trim())
                            .icon(icon)
                            .build()));

            recipe.getRecipeIngredients().add(RecipeIngredient.builder()
                    .recipe(recipe)
                    .ingredient(ingredient)
                    .quantity(quantity)
                    .build());
        }
        recipeRepository.save(recipe);
    }
}
