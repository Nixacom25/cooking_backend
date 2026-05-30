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
    private final com.cooked.backend.service.TaxonomyService taxonomyService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Maps moved to TaxonomyService

    @Override
    @Transactional
    public void seedExploreData() {
        log.info("Checking for Explore Data updates in explore_recipes.json...");

        // Drop ALL unique constraints on recipes, cookbooks, ingredients, etc. to allow duplicates as requested
        try {
            String sql = "DO $$ " +
                        "DECLARE r RECORD; " +
                        "BEGIN " +
                        "  -- Supprimer les contraintes d'unicité sur les tables clés\n" +
                        "  FOR r IN (SELECT conname, relname FROM pg_constraint c JOIN pg_class cl ON c.conrelid = cl.oid WHERE cl.relname IN ('recipes', 'cookbooks', 'ingredients', 'recipe_data') AND contype = 'u') " +
                        "  LOOP " +
                        "    BEGIN " +
                        "      EXECUTE 'ALTER TABLE ' || r.relname || ' DROP CONSTRAINT IF EXISTS ' || r.conname; " +
                        "    EXCEPTION WHEN OTHERS THEN " +
                        "      NULL; " +
                        "    END; " +
                        "  END LOOP; " +
                        "  -- Supprimer les index uniques\n" +
                        "  FOR r IN (SELECT indexname, tablename FROM pg_indexes WHERE tablename IN ('recipes', 'cookbooks', 'ingredients', 'recipe_data') AND indexdef LIKE '%UNIQUE INDEX%' AND indexname NOT LIKE '%_pkey') " +
                        "  LOOP " +
                        "    BEGIN " +
                        "      EXECUTE 'DROP INDEX IF EXISTS ' || r.indexname; " +
                        "    EXCEPTION WHEN OTHERS THEN " +
                        "      NULL; " +
                        "    END; " +
                        "  END LOOP; " +
                        "END $$;";
            jdbcTemplate.execute(sql);
            log.info("Successfully dropped all unique constraints from recipes, cookbooks, ingredients and recipe_data tables");
        } catch (Exception e) {
            log.warn("Could not drop constraints: {}", e.getMessage());
        }

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
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("explore_recipes.json");
            }
            
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

                        recipe.setCuisine(taxonomyService.getOrCreateCategory(cuisineName, CategoryType.CUISINE));
                        recipe.setCategory(taxonomyService.getOrCreateCategory(categoryName, CategoryType.CATEGORY));
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
            } else {
                log.error("CRITICAL ERROR: explore_recipes.json was NOT found in resources or classpath!");
            }
        } catch (Exception e) {
            log.error("Failed to seed explore data from explore.json", e);
        }
    }

    // Helper removed, now using taxonomyService

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

            Ingredient ingredient = ingredientRepository.findFirstByName(ingName.toLowerCase().trim())
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
