'use strict';

const { generateRecipes } = require('../services/recipeService');
const { generateTrendingDishes } = require('../services/aiService');
const { asyncHandler } = require('../middleware/errorHandler');
const logger = require('../utils/logger');

/**
 * POST /api/recipes/generate
 *
 * Accepts a JSON body with a validated, sanitised ingredients array and optional user
 * preferences, and returns 6-10 AI-generated recipes.
 *
 * Input: req.validatedBody (set by validateGenerateRecipes middleware)
 * {
 *   "ingredients": ["tomato", "onion", "garlic"],
 *   "user_preferences": { "allergies": [], "preferences": [] }
 * }
 *
 * Success Response (200):
 * {
 *   "success": true,
 *   "recipes": [ { ...recipe } ]
 * }
 */
const generateRecipesHandler = asyncHandler(async (req, res) => {
  const { ingredients, user_preferences } = req.validatedBody;

  logger.info(
    `Recipe generation request — ingredients: [${ingredients.join(', ')}]` +
      (user_preferences.allergies.length
        ? `, allergies: [${user_preferences.allergies.join(', ')}]`
        : '')
  );
  const startTime = Date.now();

  const recipes = await generateRecipes(ingredients, user_preferences);

  const elapsed = Date.now() - startTime;
  logger.info(`Recipe generation completed in ${elapsed}ms — returned ${recipes.length} recipes`);

  return res.status(200).json({
    success: true,
    recipes,
    allowed_ingredients: [],
    restricted_ingredients: [],
    image_url: null,
  });
});

/**
 * GET /api/recipes/trending
 * 
 * Returns a list of 10 trending dish names.
 */
const getTrendingDishesHandler = asyncHandler(async (req, res) => {
  logger.info('Trending dishes request');
  const trending = await generateTrendingDishes();

  return res.status(200).json({
    success: true,
    trending
  });
});

module.exports = { generateRecipesHandler, getTrendingDishesHandler };
