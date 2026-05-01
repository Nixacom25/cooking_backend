'use strict';

const { Router } = require('express');
const { upload, handleUploadError } = require('../middleware/upload');
const { validateAnalyzeBody } = require('../validators/ingredientValidator');
const { analyzeHandler } = require('../controllers/analyzeController');
const { aiEndpointLimiter } = require('../middleware/rateLimiter');

const router = Router();

/**
 * POST /api/analyze
 *
 * Combined fridge/pantry scan + recipe generation in a single request.
 *
 * Middleware chain:
 * 1. aiEndpointLimiter     — protects against OpenAI cost abuse (strictest limit)
 * 2. upload.single('image') — multer parses multipart/form-data; stores file in memory
 * 3. handleUploadError     — converts MulterError to standard error envelope
 * 4. validateAnalyzeBody    — validates user_id, user_preferences, manual_ingredients, ingredients
 * 5. analyzeHandler        — orchestrates detection + preference lookup + recipe generation
 *
 * Input (one of):
 *   multipart/form-data  → field: image (binary), optional: user_id, user_preferences (JSON string)
 *   application/json     → field: image_base64 (data URI), optional: user_id, user_preferences
 */
router.post(
  '/',
  aiEndpointLimiter,
  upload.single('image'),
  handleUploadError,
  validateAnalyzeBody,
  analyzeHandler
);

module.exports = router;
