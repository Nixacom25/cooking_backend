'use strict';

require('dotenv').config();

/**
 * Centralized application configuration.
 * Validates required environment variables at startup to fail fast.
 */

const REQUIRED_VARS = [
  'OPENAI_API_KEY',
  'MONGODB_URI',
  'MONGODB_DB_NAME',
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
  httpTimeoutMs: parseInt(process.env.HTTP_TIMEOUT_MS || '90000', 10),

  openai: {
    apiKey: process.env.OPENAI_API_KEY,
    /** Vision model for ingredient detection */
    visionModel: process.env.OPENAI_VISION_MODEL || 'gpt-4o',
    /** Fast model for recipe generation and ingredient filtering */
    generationModel: process.env.OPENAI_GENERATION_MODEL || 'gpt-4o-mini',
    /** Image generation model via the same OPENAI_API_KEY.
     *  Returns base64 (response_format:'b64_json'), uploaded to Cloudinary for permanent URLs. */
    imageModel: process.env.OPENAI_IMAGE_MODEL || 'dall-e-3',
    /** Request timeout in milliseconds */
    timeout: parseInt(process.env.OPENAI_TIMEOUT_MS || '45000', 10),
  },

  mongodb: {
    uri: process.env.MONGODB_URI,
    dbName: process.env.MONGODB_DB_NAME || 'ai_recipe_app',
    options: {
      maxPoolSize: 10,
      serverSelectionTimeoutMS: 5000,
      socketTimeoutMS: 45000,
    },
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
