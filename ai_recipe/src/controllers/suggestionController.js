'use strict';

const { generateRecipes } = require('../services/recipeService');
const { asyncHandler } = require('../middleware/errorHandler');
const logger = require('../utils/logger');

/**
 * POST /api/recipes/suggest
 * 
 * Generates initial recipe suggestions for a user based on their preferences.
 * No ingredients required.
 */
const suggestRecipesHandler = asyncHandler(async (req, res) => {
  const { user_preferences: prefs, ingredients, recipe_pool } = req.validatedBody;

  logger.info(`Recipe suggestions request (onboarding) ${recipe_pool ? 'with recipe pool' : ''}`);
  const startTime = Date.now();

  // Call generateRecipes with empty ingredient list and optional pool
  const recipes = await generateRecipes([], { ...prefs, recipe_pool });

  const elapsed = Date.now() - startTime;
  logger.info(`Recipe suggestions completed in ${elapsed}ms — returned ${recipes.length} recipes`);

  return res.status(200).json({
    success: true,
    recipes,
  });
});

module.exports = { suggestRecipesHandler };
