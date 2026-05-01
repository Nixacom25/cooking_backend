'use strict';

const { uploadImage } = require('./storageService');
const { detectIngredientsFromImage, filterPreDetectedIngredients } = require('./aiService');
const logger = require('../utils/logger');
const { AppError } = require('../middleware/errorHandler');

/**
 * Runs the full ingredient detection pipeline (Stateless).
 *
 * @param {Buffer} fileBuffer - Raw image bytes
 * @param {string} originalName - Original filename
 * @param {object} userPreferences - { allergies: string[], preferences: string[] }
 */
async function detectIngredients(fileBuffer, originalName, userPreferences = {}) {
  const startedAt = Date.now();
  logger.info(`Ingredient detection pipeline started for ${originalName}`);

  // Step 1: Upload to Cloudinary (Required for OpenAI Vision)
  const imageUrl = await uploadImage(fileBuffer, originalName);
  logger.info(`Source image uploaded: ${imageUrl}`);

  // Step 2: Call OpenAI vision model
  const resultData = await detectIngredientsFromImage(imageUrl, userPreferences);

  // Step 3: Check if any ingredients were found
  if (resultData.allowed_ingredients.length === 0 && resultData.restricted_ingredients.length === 0) {
    throw new AppError(
      422,
      'NO_INGREDIENTS_FOUND',
      'The vision model could not detect any food ingredients'
    );
  }

  const result = { ...resultData, image_url: imageUrl };
  logger.info(`Ingredient detection completed in ${Date.now() - startedAt}ms`);

  return result;
}

/**
 * Runs the filtering pipeline for pre-detected ingredient lists (Stateless).
 *
 * @param {string[]} ingredientList - List of ingredient names
 * @param {object} userPreferences - { allergies: string[], preferences: string[] }
 */
async function detectIngredientsFromList(ingredientList, userPreferences = {}) {
  const startedAt = Date.now();
  logger.info(`Ingredient list filtering started for ${ingredientList.length} items`);

  const result = await filterPreDetectedIngredients(ingredientList, userPreferences);

  logger.info(`Ingredient list filtering completed in ${Date.now() - startedAt}ms`);
  return result;
}

/**
 * Runs ingredient detection for a pre-existing public image URL (Stateless).
 */
async function detectIngredientsFromUrl(imageUrl, userPreferences = {}) {
  const startedAt = Date.now();
  logger.info(`Ingredient detection from URL started: ${imageUrl}`);

  const resultData = await detectIngredientsFromImage(imageUrl, userPreferences);

  if (resultData.allowed_ingredients.length === 0 && resultData.restricted_ingredients.length === 0) {
    throw new AppError(
      422,
      'NO_INGREDIENTS_FOUND',
      'The vision model could not detect any food ingredients in the provided URL'
    );
  }

  const result = { ...resultData, image_url: imageUrl };
  logger.info(`Ingredient detection from URL completed in ${Date.now() - startedAt}ms`);

  return result;
}

module.exports = { detectIngredients, detectIngredientsFromList, detectIngredientsFromUrl };
