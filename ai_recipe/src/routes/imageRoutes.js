'use strict';

const { Router } = require('express');
const { generateImageHandler } = require('../controllers/imageController');
const { aiEndpointLimiter } = require('../middleware/rateLimiter');

const router = Router();

/**
 * POST /api/images/generate
 * 
 * Generates a high-quality AI food image for a given dish name.
 */
router.post(
  '/generate',
  aiEndpointLimiter,
  generateImageHandler
);

module.exports = router;
