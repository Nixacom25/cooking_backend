'use strict';

const { detectIngredients } = require('../services/ingredientService');
const { generateRecipes } = require('../services/recipeService');
const { asyncHandler, AppError } = require('../middleware/errorHandler');
const { isValidImageBuffer } = require('../validators/ingredientValidator');
const config = require('../config/index');
const logger = require('../utils/logger');

/**
 * POST /api/analyze
 * Stateless version: ignores user_id (since DB is disabled) and relies on inline user_preferences.
 */
const analyzeHandler = asyncHandler(async (req, res) => {
  const startTime = Date.now();

  // Resolve user identity and preferences
  const { user_preferences: inlinePrefs, manual_ingredients: manualIngredients, ingredients: textIngredients } = req.validatedBody;

  // Stateless mode: Always use inline preferences passed by the caller (the Spring Boot backend)
  const userPreferences = inlinePrefs || {
    allergies: [],
    preferences: [],
    dislikes: [],
    cuisines_love: [],
    kitchen_tools: [],
    cooking_goals: [],
    DNA: {},
    skill_level: { label: 'HomeCook', percent: 50 },
    time_minutes: null,
    servings: null
  };

  // Resolve ingredient source — image OR text list
  let allowedNames;
  let ingredientResult;

  if (textIngredients && textIngredients.length > 0) {
    // Text-only path: caller supplied an ingredients array — skip Vision entirely
    logger.info(`Analyze (text-only) — ${textIngredients.length} ingredients`);
    allowedNames = textIngredients;
    ingredientResult = { 
        allowed_ingredients: textIngredients.map((n) => ({ name: n, confidence: 1.0 })), 
        restricted_ingredients: [], 
        image_url: null 
    };
  } else {
    // Image path — resolve buffer then call Vision API
    let buffer;
    let originalname;

    if (req.body.image_base64) {
      const match = req.body.image_base64.match(/^data:image\/(jpeg|png|webp);base64,/);
      if (!match) {
        throw new AppError(400, 'INVALID_IMAGE', 'Base64 image must have a valid data URI prefix.');
      }
      const base64Data = req.body.image_base64.replace(/^data:image\/\w+;base64,/, '');
      buffer = Buffer.from(base64Data, 'base64');
      originalname = `analyze_${Date.now()}.${match[1]}`;
    } else if (req.file) {
      buffer = req.file.buffer;
      originalname = req.file.originalname;
    } else {
      throw new AppError(400, 'INVALID_IMAGE', 'No image provided.');
    }

    ingredientResult = await detectIngredients(buffer, originalname, userPreferences);
    allowedNames = ingredientResult.allowed_ingredients.map((i) => i.name);

    if (allowedNames.length === 0 && manualIngredients.length === 0) {
      throw new AppError(422, 'NO_INGREDIENTS_FOUND', 'No usable ingredients were detected.');
    }
  }

  // Merge manual_ingredients
  if (manualIngredients && manualIngredients.length > 0) {
    const existing = new Set(allowedNames.map((n) => n.toLowerCase()));
    for (const name of manualIngredients) {
      if (!existing.has(name.toLowerCase())) {
        allowedNames.push(name);
        ingredientResult.allowed_ingredients.push({ name, confidence: 1.0 });
      }
    }
  }

  // Generate personalised recipes
  const recipes = await generateRecipes(allowedNames, userPreferences);

  return res.status(200).json({
    success: true,
    allowed_ingredients: ingredientResult.allowed_ingredients,
    restricted_ingredients: ingredientResult.restricted_ingredients,
    image_url: ingredientResult.image_url,
    recipes,
  });
});

module.exports = { analyzeHandler };
