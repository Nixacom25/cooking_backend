const { searchRecipes } = require('../services/search.service');
const { asyncHandler } = require('../middleware/errorHandler');

/**
 * Handle recipe search requests.
 * GET /api/search?q=query
 */
const searchHandler = asyncHandler(async (req, res) => {
    const { q } = req.query;

    if (!q) {
        return res.status(400).json({
            success: false,
            error: {
                code: 'BAD_REQUEST',
                message: "Le paramètre de recherche 'q' est manquant"
            }
        });
    }

    const results = await searchRecipes(q);

    return res.status(200).json({
        success: true,
        data: results,
        message: `${results.length} résultats trouvés`
    });
});

module.exports = { searchHandler };
