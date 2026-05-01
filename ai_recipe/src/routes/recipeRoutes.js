'use strict';

const { Router } = require('express');
const { validateGenerateRecipes, validateSuggestRecipes } = require('../validators/recipeValidator');
const { generateRecipesHandler, getTrendingDishesHandler } = require('../controllers/recipeController');
const { suggestRecipesHandler } = require('../controllers/suggestionController');
const { aiEndpointLimiter } = require('../middleware/rateLimiter');

const router = Router();

/**
 * GET /api/recipes/trending
 * 
 * Returns a list of trending dish names.
 */
router.get(
  '/trending',
  getTrendingDishesHandler
);

/**
 * POST /api/recipes/suggest
 * 
 * Generates initial suggestions for new users based on preferences.
 */
router.post(
  '/suggest',
  aiEndpointLimiter,
  validateSuggestRecipes,
  suggestRecipesHandler
);

/**
 * POST /api/recipes/generate
 *
 * Middleware chain:
 * 1. aiEndpointLimiter       — protects against OpenAI cost abuse
 * 2. validateGenerateRecipes — Joi schema + sanitisation; attaches req.validatedBody
 * 3. generateRecipesHandler  — runs the generation pipeline
 */
router.post(
  '/generate',
  aiEndpointLimiter,
  validateGenerateRecipes,
  generateRecipesHandler
);

module.exports = router;
