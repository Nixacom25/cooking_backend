const axios = require('axios');
const cheerio = require('cheerio');
const { AppError } = require('../middleware/errorHandler');

/**
 * Searches for recipes on the web using Ecosia (lightweight version).
 * 
 * @param {string} query - The search term (e.g., "Lasagna recipe")
 * @returns {Promise<Array>} List of search results
 */
const searchRecipes = async (query) => {
    if (!query) {
        throw new AppError(400, 'BAD_REQUEST', "Le terme de recherche est requis");
    }

    const searchQuery = query.toLowerCase().includes('recipe') || query.toLowerCase().includes('recette')
        ? query
        : `${query} recipe`;

    const url = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(searchQuery)}`;

    try {
        console.log(`[Search] Attempting DuckDuckGo HTML search for: ${searchQuery}`);
        const { data } = await axios.get(url, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
            },
            timeout: 10000
        });

        const $ = cheerio.load(data);
        const results = [];

        $('.result__body').each((i, el) => {
            if (i >= 10) return;
            const titleEl = $(el).find('.result__title a');
            const title = titleEl.text().trim();
            const link = titleEl.attr('href');
            const snippet = $(el).find('.result__snippet').text().trim();

            if (title && link) {
                // DuckDuckGo results sometimes have internal redirect links, 
                // but usually they are direct for the HTML version.
                results.push({
                    title,
                    url: link.startsWith('http') ? link : 'https:' + link,
                    snippet,
                    thumbnail: null
                });
            }
        });

        console.log(`[Search] Found ${results.length} results via DuckDuckGo`);
        return results;
    } catch (error) {
        console.error("[Search] DuckDuckGo Error Details:");
        if (error.response) {
            console.error(`Status: ${error.response.status}`);
        } else {
            console.error(`Error Message: ${error.message}`);
        }
        throw new AppError(502, 'BAD_GATEWAY', `Échec de la recherche : ${error.message}`);
    }
};

module.exports = { searchRecipes };
