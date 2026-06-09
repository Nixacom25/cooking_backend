'use strict';

require('dotenv').config();

/**
 * Centralized application configuration.
 * Validates required environment variables at startup to fail fast.
 */

const REQUIRED_VARS = [
  'OPENAI_API_KEY',
  'REDIS_URL',
  'CLOUDINARY_CLOUD_NAME',
  'CLOUDINARY_API_KEY',
  'CLOUDINARY_API_SECRET',
];

function validateEnv() {
  const missing = REQUIRED_VARS.filter((key) => !process.env[key]);
  if (missing.length > 0) {
    throw new Error(
      `Missing required environment variables: ${missing.join(', ')}\n` +
      'Please copy .env.example to .env and fill in the values.'
    );
  }
}

// Only validate in non-test environments
if (process.env.NODE_ENV !== 'test') {
  validateEnv();
}

const config = {
  env: process.env.NODE_ENV || 'development',
  port: parseInt(process.env.PORT, 10) || 3000,
  internalSecret: process.env.INTERNAL_API_SECRET || 'cooked_internal_bypass_secret_2024',
  httpTimeoutMs: parseInt(process.env.HTTP_TIMEOUT_MS || '90000', 10),

  openai: {
    apiKey: process.env.OPENAI_API_KEY,
    visionModel: process.env.OPENAI_VISION_MODEL || 'gpt-4o',
    generationModel: process.env.OPENAI_GENERATION_MODEL || 'gpt-4o-mini',
    imageModel: process.env.OPENAI_IMAGE_MODEL || 'dall-e-3',
    timeout: parseInt(process.env.OPENAI_TIMEOUT_MS || '90000', 10),
  },

  redis: {
    url: process.env.REDIS_URL,
    /** TTL for ingredient detection cache (24 hours in seconds) */
    ingredientTTL: 60 * 60 * 24,
    /** TTL for recipe generation cache (1 hour in seconds) */
    recipeTTL: 60 * 60,
  },

  cloudinary: {
    cloudName: process.env.CLOUDINARY_CLOUD_NAME,
    apiKey: process.env.CLOUDINARY_API_KEY,
    apiSecret: process.env.CLOUDINARY_API_SECRET,
    folder: process.env.CLOUDINARY_FOLDER || 'ai-recipe-app/ingredients',
  },

  upload: {
    /** Maximum file size: 10 MB */
    maxFileSizeBytes: 10 * 1024 * 1024,
    allowedMimeTypes: ['image/jpeg', 'image/png', 'image/webp'],
    allowedExtensions: ['jpg', 'jpeg', 'png', 'webp'],
  },

  rateLimit: {
    /** Window duration in milliseconds (15 minutes) */
    windowMs: 15 * 60 * 1000,
    /** Max requests per window per IP */
    maxRequests: 100,
    /** Max AI-heavy requests per window (stricter to control costs) */
    aiMaxRequests: parseInt(process.env.AI_RATE_LIMIT ||
      (process.env.NODE_ENV === 'production' ? 20 : 500), 10),
  },

  cors: {
    /** Origins allowed for CORS — tighten in production */
    origins: process.env.CORS_ORIGINS
      ? process.env.CORS_ORIGINS.split(',').map((o) => o.trim())
      : ['*'],
  },
};

module.exports = config;
