'use strict';

const { detectIngredients, detectIngredientsFromList, detectIngredientsFromUrl } = require('../services/ingredientService');
const { asyncHandler, AppError } = require('../middleware/errorHandler');
const { isValidImageBuffer } = require('../validators/ingredientValidator');
const config = require('../config/index');
const logger = require('../utils/logger');
const { normaliseDNA, normaliseSkillLevel, normaliseTimePreference, normaliseServingsPreference, normaliseCuisinesLove, normaliseKitchenTools, normaliseCookingGoals } = require('../utils/dna');

/**
 * POST /api/ingredients/detect
 *
 * Accepts a multipart/form-data image upload, a base64 encoded image, or a pre-detected
 * ingredients array, and returns categorized food ingredients based on user preferences.
 *
 * Success Response (200):
 * {
 *   "success": true,
 *   "allowed_ingredients": [ { "name": "tomato", "confidence": 0.97 } ],
 *   "restricted_ingredients": [ { "ingredient": "milk", "reason": "Allergic to dairy" } ],
 *   "image_url": "https://res.cloudinary.com/..."
 * }
 */
const detectIngredientsHandler = asyncHandler(async (req, res) => {
  // userPreferences is now populated and validated by the validateUserPreferences middleware
  const userPreferences = req.body.user_preferences || {
    allergies: [],
    preferences: [],
    dislikes: [],
    cuisines_love: normaliseCuisinesLove(),
    kitchen_tools: normaliseKitchenTools(),
    cooking_goals: normaliseCookingGoals(),
    DNA: normaliseDNA(),
    skill_level: normaliseSkillLevel(),
    time_minutes: normaliseTimePreference(),
    servings: normaliseServingsPreference(),
  };

  // Case 3: Pre-detected ingredients array
  if (req.body.ingredients && Array.isArray(req.body.ingredients)) {
    logger.info(`Ingredient detection request — pre-detected list (${req.body.ingredients.length} items)`);
    const startTime = Date.now();
    const result = await detectIngredientsFromList(req.body.ingredients, userPreferences);
    const elapsed = Date.now() - startTime;
    logger.info(`Ingredient filtering completed in ${elapsed}ms — allowed: ${result.allowed_ingredients.length}`);
    return res.status(200).json({ success: true, ...result, image_url: null });
  }

  // Case 1: Pre-existing public image URL
  if (req.body.image_url) {
    const imageUrl = String(req.body.image_url).trim();
    if (!imageUrl.startsWith('http://') && !imageUrl.startsWith('https://')) {
      throw new AppError(400, 'INVALID_IMAGE', 'image_url must be a valid http or https URL.');
    }
    logger.info(`Ingredient detection request — image_url: ${imageUrl}`);
    const startTime = Date.now();
    const result = await detectIngredientsFromUrl(imageUrl, userPreferences);
    const elapsed = Date.now() - startTime;
    logger.info(`Ingredient detection (URL) completed in ${elapsed}ms — allowed: ${result.allowed_ingredients.length}`);
    return res.status(200).json({ success: true, ...result });
  }

  // Case 2 & 3: Image buffer (base64 or file upload)
  let buffer;
  let originalname;

  if (req.body.image_base64) {
    logger.info(`Ingredient detection request — base64 image`);

    // Validate MIME type against allowed formats
    const match = req.body.image_base64.match(/^data:image\/(jpeg|png|webp);base64,/);
    if (!match) {
      throw new AppError(400, 'INVALID_IMAGE', 'Base64 image must have a valid data URI prefix (jpeg, png, or webp).');
    }

    // Remove data:image/jpeg;base64, prefix
    const base64Data = req.body.image_base64.replace(/^data:image\/\w+;base64,/, '');
    buffer = Buffer.from(base64Data, 'base64');

    // ENFORCE SIZE LIMIT (10MB) for Base64 decoded buffer
    if (buffer.byteLength > config.upload.maxFileSizeBytes) {
      throw new AppError(400, 'INVALID_IMAGE', `Image size exceeds the 10 MB limit (decoded size: ${(buffer.byteLength / 1024 / 1024).toFixed(2)} MB).`);
    }

    // Validate magic bytes — reject corrupt/non-image base64 data
    if (!isValidImageBuffer(buffer)) {
      throw new AppError(400, 'INVALID_IMAGE', 'Base64 data does not contain a valid JPEG, PNG, or WebP image.');
    }

    originalname = `base64_upload_${Date.now()}.${match[1]}`; // Inherit matched extension
  } else if (req.file) {
    buffer = req.file.buffer;
    originalname = req.file.originalname;
    logger.info(`Ingredient detection request — file: ${originalname} (${req.file.size} bytes)`);
  } else {
    throw new AppError(400, 'INVALID_IMAGE', 'No image file, base64 data, image_url, or ingredients array provided.');
  }

  const startTime = Date.now();
  const result = await detectIngredients(buffer, originalname, userPreferences);
  const elapsed = Date.now() - startTime;

  logger.info(`Ingredient detection completed in ${elapsed}ms — allowed: ${result.allowed_ingredients.length}, restricted: ${result.restricted_ingredients.length}`);

  return res.status(200).json({
    success: true,
    ...result, // Spreads allowed_ingredients, restricted_ingredients, image_url
  });
});

module.exports = { detectIngredientsHandler };
