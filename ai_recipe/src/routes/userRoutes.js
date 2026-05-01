'use strict';

const express = require('express');
const { me } = require('../controllers/user.controller');

const router = express.Router();

/**
 * @swagger
 * /api/users:
 *   get:
 *     summary: Get current user profile (Test)
 *     tags:
 *       - Users
 *     responses:
 *       200:
 *         description: Success
 */
router.get('/', me);

module.exports = router;
