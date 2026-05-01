'use strict';

const express = require('express');
const { extractRecipe } = require('../controllers/extractController');
const { aiEndpointLimiter } = require('../middleware/rateLimiter');

const router = express.Router();

/**
 * @swagger
 * /api/extract:
 *   post:
 *     summary: Extract recipe from a URL
 *     description: Accepts a social media or web URL and returns parsed recipe data.
 *     tags:
 *       - Extraction
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - url
 *             properties:
 *               url:
 *                 type: string
 *                 format: uri
 *                 description: Source content URL from YouTube, TikTok, Instagram, or a website.
 *                 example: https://youtu.be/RaLzxZryEoA?si=uU0mXBgki2MjvtgS
 *     responses:
 *       200:
 *         description: Recipe extracted successfully
 *       400:
 *         description: Missing url in request body
 *       422:
 *         description: No description available to extract recipe
 *       500:
 *         description: Internal server error
 */
router.post('/', aiEndpointLimiter, extractRecipe);

module.exports = router;