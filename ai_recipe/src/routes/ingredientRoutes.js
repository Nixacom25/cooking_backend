'use strict';

const { Router } = require('express');
const { upload, handleUploadError } = require('../middleware/upload');
const { validateAnalyzeBody } = require('../validators/ingredientValidator');
const { analyzeHandler } = require('../controllers/analyzeController');
const { aiEndpointLimiter } = require('../middleware/rateLimiter');

const router = Router();

/**
 * POST /api/ingredients/detect
 * 
 * Legacy endpoint used by older backend versions.
 * Now aliased to analyzeHandler for convenience.
 */
router.post(
  '/detect',
  aiEndpointLimiter,
  upload.single('image'),
  handleUploadError,
  validateAnalyzeBody,
  analyzeHandler
);

module.exports = router;
