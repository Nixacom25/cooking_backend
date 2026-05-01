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

    const url = `https://www.ecosia.org/search?q=${encodeURIComponent(searchQuery)}`;

    let browser;
    try {
        browser = await chromium.launch({
            headless: true,
            args: [
                '--no-sandbox', 
                '--disable-setuid-sandbox',
                '--disable-dev-shm-usage',
                '--disable-accelerated-2d-canvas',
                '--disable-gpu'
            ]
        });
        const context = await browser.newContext({
            userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
        });
        const page = await context.newPage();

        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 20000 });

        // Wait for results to appear
        await page.waitForSelector('article[data-test-id="mainline-result"]', { timeout: 10000 }).catch(() => { });

        const results = await page.evaluate(() => {
            const items = Array.from(document.querySelectorAll('article[data-test-id="mainline-result"]'));
            return items.slice(0, 10).map(el => {
                const titleEl = el.querySelector('h2 a');
                const snippetEl = el.querySelector('.result-snippet, .result-body');

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
        console.error("Search error:", error.message);
        throw new AppError(502, 'BAD_GATEWAY', "Échec de la recherche sur le web via Ecosia");
    } finally {
        if (browser) await browser.close();
    }
};

module.exports = { searchRecipes };
