'use strict';

const { generateRecipeImage } = require('../services/aiService');
const { asyncHandler, AppError } = require('../middleware/errorHandler');
const logger = require('../utils/logger');

/**
 * POST /api/images/generate
 * 
 * Generates a professional food photograph for a given dish name.
 * Returns a permanent Cloudinary URL.
 */
const generateImageHandler = asyncHandler(async (req, res) => {
  const { name } = req.body;

  if (!name || typeof name !== 'string' || !name.trim()) {
    throw new AppError(400, 'BAD_REQUEST', 'Missing "name" field in request body.');
  }

  logger.info(`Standalone image generation request for: "${name}"`);
  const imageUrl = await generateRecipeImage(name);

  if (!imageUrl) {
    throw new AppError(500, 'AI_SERVICE_ERROR', 'Failed to generate image for the specified dish.');
  }

  return res.status(200).json({
    success: true,
    image_url: imageUrl
  });
});

module.exports = { generateImageHandler };
