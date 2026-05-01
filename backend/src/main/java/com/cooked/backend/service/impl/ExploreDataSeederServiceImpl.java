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

    private static final Map<String, String> INGREDIENT_ICONS = Map.ofEntries(
        Map.entry("spaghetti", "🍝"),
        Map.entry("ground beef", "🥩"),
        Map.entry("tomato sauce", "🥫"),
        Map.entry("onion", "🧅"),
        Map.entry("garlic", "🧄"),
        Map.entry("pizza dough", "🍕"),
        Map.entry("tomato puree", "🥫"),
        Map.entry("fresh mozzarella", "🧀"),
        Map.entry("fresh basil", "🌿"),
        Map.entry("olive oil", "🫒"),
        Map.entry("pork shoulder", "🥩"),
        Map.entry("pineapple", "🍍"),
        Map.entry("tortillas", "🫓"),
        Map.entry("cilantro", "🌿"),
        Map.entry("avocado", "🥑"),
        Map.entry("lime", "🍋‍🟩"),
        Map.entry("jalapeño", "🌶️"),
        Map.entry("sushi rice", "🍚"),
        Map.entry("nori sheets", "🍱"),
        Map.entry("fresh salmon", "🐟"),
        Map.entry("cucumber", "🥒"),
        Map.entry("ramen noodles", "🍜"),
        Map.entry("pork belly", "🥓"),
        Map.entry("soft boiled egg", "🥚"),
        Map.entry("miso paste", "🥣"),
        Map.entry("scallions", "🥬"),
        Map.entry("eggplant", "🍆"),
        Map.entry("zucchini", "🥒"),
        Map.entry("red bell pepper", "🫑"),
        Map.entry("herbes de provence", "🌿"),
        Map.entry("chicken breast", "🍗"),
        Map.entry("quinoa", "🍚"),
        Map.entry("cherry tomatoes", "🍅"),
        Map.entry("hummus", "🥣"),
        Map.entry("chickpeas", "🫘"),
        Map.entry("parsley", "🌿"),
        Map.entry("cumin", "🧂"),
        Map.entry("flour", "🌾"),
        Map.entry("chicken", "🍗"),
        Map.entry("shawarma spices", "🧂"),
        Map.entry("pita bread", "🫓"),
        Map.entry("garlic sauce", "🧴"),
        Map.entry("rice", "🍚"),
        Map.entry("beef", "🥩"),
        Map.entry("spinach", "🥬"),
        Map.entry("carrots", "🥕"),
        Map.entry("gochujang", "🌶️"),
        Map.entry("shrimp", "🦐"),
        Map.entry("tofu", "🧊"),
        Map.entry("peanuts", "🥜"),
        Map.entry("bean sprouts", "🌱"),
        Map.entry("coconut milk", "🥥"),
        Map.entry("green curry paste", "🍛"),
        Map.entry("bamboo shoots", "🎋"),
        Map.entry("soy sauce", "🧂"),
        Map.entry("ginger", "🫚"),
        Map.entry("broccoli", "🥦"),
        Map.entry("butter", "🧈"),
        Map.entry("heavy cream", "🥛"),
        Map.entry("turmeric", "🧂"),
        Map.entry("garam masala", "🧂"),
        Map.entry("mushrooms", "🍄"),
        Map.entry("egg", "🥚"),
        Map.entry("eggs", "🥚"),
        Map.entry("bacon", "🥓"),
        Map.entry("cheese", "🧀"),
        Map.entry("parmesan", "🧀"),
        Map.entry("pasta", "🍝"),
        Map.entry("salmon", "🐟"),
        Map.entry("tuna", "🐟"),
        Map.entry("potato", "🥔"),
        Map.entry("potatoes", "🥔"),
        Map.entry("carrot", "🥕"),
        Map.entry("lemon", "🍋"),
        Map.entry("honey", "🍯"),
        Map.entry("sugar", "🧂"),
        Map.entry("salt", "🧂"),
        Map.entry("pepper", "🧂"),
        Map.entry("bread", "🍞"),
        Map.entry("milk", "🥛"),
        Map.entry("oil", "🧴"),
        Map.entry("vinegar", "🧴")
    );

    @Override
    @Transactional
    public void seedExploreData() {
        log.info("Checking if Explore Data needs seeding...");

        // Check if we already have explore recipes to avoid re-running every time
        long count = recipeRepository.countByOrigin(RecipeOrigin.EXPLORE);
        if (count > 0) {
            log.info("Explore Data already exists ({} recipes). Skipping seed.", count);
            return;
        }

        log.info("Seeding Explore Data...");

        Optional<User> existingUser = userRepository.findByEmail("explore@cooked.com");
        User systemUser;
        if (existingUser.isPresent()) {
            systemUser = existingUser.get();
        } else {
            log.info("Creating new System User...");
            User newUser = new User();
            newUser.setFirstname("Cooked");
            newUser.setLastname("Official");
            newUser.setEmail("explore@cooked.com");
            newUser.setPassword("system_no_login");
            newUser.setRole(Role.ADMIN);
            newUser.setStatus(Status.ACTIVE);
            newUser.setProvider(Provider.LOCAL);
            newUser.setResendCount(0);
            systemUser = userRepository.save(newUser);
        }

        // Load recipes from JSON
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getResourceAsStream("/explore_recipes.json");
            if (is != null) {
                List<Map<String, Object>> recipesData = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
                log.info("Found {} recipes in JSON. Starting seed...", recipesData.size());
                
                for (Map<String, Object> data : recipesData) {
                    try {
                        String name = (String) data.get("name");
                        List<Map<String, String>> ingredients = (List<Map<String, String>>) data.get("ingredients");
                        List<String> steps = (List<String>) data.get("steps");
                        List<String> equipment = (List<String>) data.get("equipment");

                        // Find existing or create new
                        Recipe recipe = recipeRepository.findByUserIdAndName(systemUser.getId(), name)
                                .orElseGet(() -> Recipe.builder()
                                        .user(systemUser)
                                        .name(name)
                                        .origin(RecipeOrigin.EXPLORE)
                                        .isPublic(true)
                                        .build());

                        // Update fields
                        recipe.setCuisine((String) data.get("cuisine"));
                        recipe.setCategory((String) data.get("category"));
                        recipe.setPrepTime(data.get("prepTime") != null ? (Integer) data.get("prepTime") : 0);
                        recipe.setCookTime(data.get("cookTime") != null ? (Integer) data.get("cookTime") : 0);
                        recipe.setKcal(data.get("kcal") != null ? (Integer) data.get("kcal") : 0);
                        recipe.setSteps(new ArrayList<>(steps));
                        recipe.setEquipment(new ArrayList<>(equipment));
                        recipe.setServings(2);

                        Recipe saved = recipeRepository.save(recipe);
                        updateIngredients(saved, ingredients);
                    } catch (Exception e) {
                        log.error("Failed to create recipe: {}", data.get("name"), e);
                    }
                }
            } else {
                log.warn("explore_recipes.json not found in classpath!");
            }
        } catch (Exception e) {
            log.error("Failed to seed explore data from JSON", e);
        }

        log.info("Explore data seeding complete!");
    }

    private void updateIngredients(Recipe recipe, List<Map<String, String>> ingredients) {
        if (recipe.getRecipeIngredients() == null) {
            recipe.setRecipeIngredients(new HashSet<>());
        } else {
            recipe.getRecipeIngredients().clear();
        }

        if (ingredients != null) {
            for (Map<String, String> ingData : ingredients) {
                String ingName = ingData.get("name");
                String quantity = ingData.get("quantity") != null ? ingData.get("quantity") : "1 unit";
                String lowerName = ingName.toLowerCase();
                String icon = INGREDIENT_ICONS.getOrDefault(lowerName, "🍲");

                Ingredient ingredient = ingredientRepository.findByName(lowerName)
                        .map(existing -> {
                            existing.setIcon(icon);
                            return ingredientRepository.save(existing);
                        })
                        .orElseGet(() -> ingredientRepository.save(Ingredient.builder()
                                .name(lowerName)
                                .icon(icon)
                                .build()));

                recipe.getRecipeIngredients().add(RecipeIngredient.builder()
                        .recipe(recipe)
                        .ingredient(ingredient)
                        .quantity(quantity)
                        .build());
            }
        }
        recipeRepository.save(recipe);
    }
}
