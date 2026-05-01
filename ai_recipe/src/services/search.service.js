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

    const url = `https://www.ecosia.org/search?q=${encodeURIComponent(searchQuery)}`;

    try {
        const { data } = await axios.get(url, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
            },
            timeout: 10000
        });

        const $ = cheerio.load(data);
        const results = [];

        $('.result').each((i, el) => {
            if (i >= 10) return;
            const title = $(el).find('.result-title').text().trim();
            const link = $(el).find('.result-title').attr('href');
            const snippet = $(el).find('.result-snippet').text().trim();

            if (title && link) {
                results.push({
                    title,
                    url: link,
                    snippet,
                    thumbnail: null
                });
            }
        });

        // Fallback for different Ecosia layout
        if (results.length === 0) {
            $('article').each((i, el) => {
                if (i >= 10) return;
                const title = $(el).find('h2').text().trim();
                const link = $(el).find('a').attr('href');
                if (title && link && link.startsWith('http')) {
                    results.push({ title, url: link, snippet: "", thumbnail: null });
                }
            });
        }

        return results;
    } catch (error) {
        console.error("Search error:", error.message);
        throw new AppError(502, 'BAD_GATEWAY', "Échec de la recherche sur le web");
    }
};

module.exports = { searchRecipes };
