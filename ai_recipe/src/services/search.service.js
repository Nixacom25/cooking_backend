const { chromium } = require('playwright');
const { AppError } = require('../middleware/errorHandler');

/**
 * Searches for recipes on the web using Bing Search via Playwright.
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

    const url = `https://www.bing.com/search?q=${encodeURIComponent(searchQuery)}`;

    let browser;
    try {
        browser = await chromium.launch({
            headless: true,
            args: ['--no-sandbox', '--disable-setuid-sandbox']
        });
        const page = await browser.newPage({
            userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
        });

        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });

        // Wait for results
        await page.waitForSelector('li.b_algo', { timeout: 10000 }).catch(() => { });

        const results = await page.evaluate(() => {
            const items = Array.from(document.querySelectorAll('li.b_algo'));
            return items.slice(0, 10).map(el => {
                const titleEl = el.querySelector('h2 a');
                const snippetEl = el.querySelector('.b_caption p, .b_snippet');

                return {
                    title: titleEl?.innerText?.trim() || "",
                    url: titleEl?.href || "",
                    snippet: snippetEl?.innerText?.trim() || "",
                    thumbnail: null
                };
            }).filter(item => item.title && item.url);
        });

        return results;
    } catch (error) {
        console.error("Search error:", error);
        throw new AppError(502, 'BAD_GATEWAY', "Échec de la recherche sur le web");
    } finally {
        if (browser) await browser.close();
    }
};

module.exports = { searchRecipes };
