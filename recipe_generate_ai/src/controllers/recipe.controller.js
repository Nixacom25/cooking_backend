import { asyncHandler } from "../utils/AsyncHandler.js";
import { ApiResponse } from "../utils/ApiResponse.js";
import {
  generateRecipesFromIngredients,
  generateRecipeSuggestions,
  generateTrendingDishes,
} from "../services/openai.service.js";

/**
 * POST /api/recipes/generate & /api/recipe/generate
 */
export const generateRecipesHandler = asyncHandler(async (req, res) => {
  const ingredients = req.body.ingredients || [];
  let userPreferences = req.body.user_preferences || {};

  if (typeof userPreferences === "string") {
    try {
      userPreferences = JSON.parse(userPreferences);
    } catch (_) {}
  }

  const recipes = await generateRecipesFromIngredients(ingredients, userPreferences);

  return res.status(200).json({
    success: true,
    recipes,
    allowed_ingredients: ingredients.map((n) => ({ name: typeof n === "string" ? n : n.name || "", quantity: "-" })),
    restricted_ingredients: [],
    image_url: null,
  });
});

/**
 * POST /api/recipes/suggest & /api/recipe/suggest
 */
export const suggestRecipesHandler = asyncHandler(async (req, res) => {
  let userPreferences = req.body.user_preferences || {};

  if (typeof userPreferences === "string") {
    try {
      userPreferences = JSON.parse(userPreferences);
    } catch (_) {}
  }

  const recipes = await generateRecipeSuggestions(userPreferences);

  return res.status(200).json({
    success: true,
    recipes,
  });
});

/**
 * GET /api/recipes/trending & /api/recipe/trending
 */
export const getTrendingDishesHandler = asyncHandler(async (req, res) => {
  const trending = generateTrendingDishes();

  return res.status(200).json({
    success: true,
    trending,
  });
});

