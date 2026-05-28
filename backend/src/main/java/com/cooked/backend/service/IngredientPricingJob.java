package com.cooked.backend.service;

import com.cooked.backend.entity.Ingredient;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.repository.IngredientRepository;
import com.cooked.backend.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredientPricingJob {

    private static final Logger logger = LoggerFactory.getLogger(IngredientPricingJob.class);

    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final AiService aiService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStartup() {
        logger.info("Application started. Running initial pricing backfill...");
        runPricingUpdate();
    }

    // Runs every day at 00:00 to verify and update ingredient prices
    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduledPricingUpdate() {
        logger.info("Starting scheduled task: Updating ingredient prices from AI Service");
        runPricingUpdate();
    }

    private void runPricingUpdate() {
        try {
            // Phase 1: Update unpriced ingredients
            List<Ingredient> unpricedIngredients = ingredientRepository.findByPriceIsNull();
            if (!unpricedIngredients.isEmpty()) {
                logger.info("Found {} unpriced ingredients. Fetching prices from AI...", unpricedIngredients.size());
                
                // Process in batches of 30 to avoid overwhelming the prompt
                int batchSize = 30;
                for (int i = 0; i < unpricedIngredients.size(); i += batchSize) {
                    List<Ingredient> batch = unpricedIngredients.subList(i, Math.min(i + batchSize, unpricedIngredients.size()));
                    List<String> names = batch.stream().map(Ingredient::getName).collect(Collectors.toList());
                    
                    Map<String, Double> prices = aiService.estimateIngredientPrices(names);
                    
                    for (Ingredient ingredient : batch) {
                        Double price = prices.getOrDefault(ingredient.getName(), 1.50); // fallback price
                        ingredient.setPrice(price);
                    }
                    ingredientRepository.saveAll(batch);
                }
                logger.info("Successfully updated ingredient prices.");
            }

            // Phase 2: Update unpriced recipes
            List<Recipe> unpricedRecipes = recipeRepository.findByTotalPriceIsNull();
            if (!unpricedRecipes.isEmpty()) {
                logger.info("Found {} unpriced recipes. Recalculating total prices...", unpricedRecipes.size());
                
                for (Recipe recipe : unpricedRecipes) {
                    double total = 0.0;
                    if (recipe.getRecipeIngredients() != null) {
                        for (var ri : recipe.getRecipeIngredients()) {
                            if (ri.getIngredient() != null && ri.getIngredient().getPrice() != null) {
                                total += ri.getIngredient().getPrice();
                            } else {
                                total += 1.50; // fallback per ingredient
                            }
                        }
                    }
                    recipe.setTotalPrice(total);
                }
                recipeRepository.saveAll(unpricedRecipes);
                logger.info("Successfully updated recipe total prices.");
            }

        } catch (Exception e) {
            logger.error("Error occurred while updating pricing data", e);
        }
    }
}
