'use strict';

const { Router } = require('express');
const { searchHandler } = require('../controllers/searchController');

const router = Router();

/**
 * GET /api/search
 * 
 * Proxy for DuckDuckGo recipe search.
 */
router.get('/', searchHandler);

module.exports = router;
