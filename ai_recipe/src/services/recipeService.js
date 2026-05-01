'use strict';

/**
 * Recipe Service — orchestrates the full recipe generation pipeline (Stateless Mode).
 */

const { generateRecipesFromIngredients, generateRecipeImage } = require('./aiService');
const logger = require('../utils/logger');
const { AppError } = require('../middleware/errorHandler');

/**
 * Runs the full recipe generation pipeline.
 *
 * @param {string[]} ingredients - Sanitised ingredient list
 * @param {object} userPreferences - { allergies: string[], preferences: string[] }
 * @returns {Promise<object[]>} Array of generated recipe objects
 */
async function generateRecipes(ingredients, userPreferences = {}) {
  const startedAt = Date.now();
  logger.info(`Recipe generation pipeline started for ${ingredients.length} ingredients`);

  // Call AI service (includes schema validation + retry logic internally)
  const validatedRecipes = await generateRecipesFromIngredients(ingredients, userPreferences);

  // Attach null image_url initially. 
  // NOTE: Cache and Database persistence removed as per request (Stateless mode).
  const recipesWithNullImages = validatedRecipes.map((r) => ({ ...r, image: null }));

  // Background image generation - non-blocking
  _backgroundGenerateImages(recipesWithNullImages).catch((err) =>
    logger.warn(`Background image generation pipeline failed: ${err.message}`)
  );

  logger.info(`Recipe generation completed in ${Date.now() - startedAt}ms`);

  return recipesWithNullImages;
}

/**
 * Generates food images for all recipes in parallel.
 * Runs in background after response.
 *
 * @param {object[]} recipes - Recipes already returned
 * @returns {Promise<void>}
 */
async function _backgroundGenerateImages(recipes) {
  const startedAt = Date.now();
  logger.info(`Background image generation started for ${recipes.length} recipes`);

  const imageResults = await Promise.allSettled(
    recipes.map((recipe) => generateRecipeImage(recipe.title))
  );

  imageResults.forEach((result, idx) => {
    const title = recipes[idx].title;
    if (result.status === 'fulfilled' && result.value) {
      logger.info(`Image generated for "${title}": ${result.value}`);
    } else {
      logger.warn(`Image generation failed for "${title}": ${result.reason || 'Unknown error'}`);
    }
  });

  logger.info(`Background image generation finished in ${Date.now() - startedAt}ms`);
}

module.exports = { generateRecipes };
