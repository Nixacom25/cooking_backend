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
        console.log(`[Search] Attempting Ecosia search for: ${searchQuery}`);
        const { data } = await axios.get(url, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
                'Accept-Language': 'en-US,en;q=0.5',
            },
            timeout: 10000
        });

        const $ = cheerio.load(data);
        const results = [];

        // Try different Ecosia selectors
        const selectors = ['.result', 'article', '.mainline-results .result'];
        
        selectors.forEach(selector => {
            if (results.length > 0) return;
            $(selector).each((i, el) => {
                if (i >= 10) return;
                const title = $(el).find('h2, .result-title').text().trim();
                const link = $(el).find('a').attr('href');
                const snippet = $(el).find('.result-snippet, .result-body, p').first().text().trim();

                if (title && link && link.startsWith('http')) {
                    results.push({ title, url: link, snippet, thumbnail: null });
                }
            });
        });

        console.log(`[Search] Found ${results.length} results`);
        return results;
    } catch (error) {
        console.error("[Search] Axios Error Details:");
        if (error.response) {
            // The request was made and the server responded with a status code
            console.error(`Status: ${error.response.status}`);
            console.error(`Data: ${JSON.stringify(error.response.data).substring(0, 500)}`);
        } else if (error.request) {
            // The request was made but no response was received
            console.error("No response received from search engine");
        } else {
            console.error(`Error Message: ${error.message}`);
        }
        throw new AppError(502, 'BAD_GATEWAY', `Échec de la recherche : ${error.message}`);
    }
};

module.exports = { searchRecipes };
