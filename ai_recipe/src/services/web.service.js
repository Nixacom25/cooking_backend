'use strict';

const { chromium } = require('playwright');
const { AppError } = require('../middleware/errorHandler');

/**
 * Extracts text content and metadata from a generic web page.
 * 
 * @param {string} url 
 * @returns {Promise<object>}
 */
const webService = async (url) => {
    const browser = await chromium.launch({ 
        headless: true,
        args: [
            '--no-sandbox', 
            '--disable-setuid-sandbox',
            '--disable-blink-features=AutomationControlled',
        ]
    });
    const context = await browser.newContext({
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        viewport: { width: 1280, height: 720 }
    });
    const page = await context.newPage();

    // Hide playwright detection
    await page.addInitScript(() => {
        Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    });

    try {
        // Use domcontentloaded instead of networkidle to avoid timeouts on busy sites
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
        
        // Wait a bit for JS to render some content
        await page.waitForTimeout(2000);

        const pageData = await page.evaluate(() => {
            // Helper to find JSON-LD recipe data
            const findRecipeJsonLd = () => {
                const scripts = Array.from(document.querySelectorAll('script[type="application/ld+json"]'));
                for (const script of scripts) {
                    try {
                        const json = JSON.parse(script.innerText);
                        // Can be a single object or an array, or a @graph
                        const items = Array.isArray(json) ? json : (json['@graph'] || [json]);
                        const recipe = items.find(item => 
                            item['@type'] === 'Recipe' || 
                            (Array.isArray(item['@type']) && item['@type'].includes('Recipe'))
                        );
                        if (recipe) return recipe;
                    } catch (e) {}
                }
                return null;
            };

            const recipeJson = findRecipeJsonLd();

            const getMetaContent = (...selectors) => {
                for (const selector of selectors) {
                    const element = document.querySelector(selector);
                    const content = element?.getAttribute("content")?.trim();
                    if (content) return content;
                }
                return null;
            };

            const resolveUrl = (value) => {
                if (!value) return null;
                try {
                    return new URL(value, window.location.href).href;
                } catch {
                    return value;
                }
            };

            const imageCandidates = [
                getMetaContent('meta[property="og:image:secure_url"]', 'meta[name="og:image:secure_url"]'),
                getMetaContent('meta[property="og:image"]', 'meta[name="og:image"]'),
                getMetaContent('meta[name="twitter:image"]', 'meta[property="twitter:image"]'),
                document.querySelector('link[rel="image_src"]')?.getAttribute("href")?.trim() || null,
            ]
                .map(resolveUrl)
                .filter(Boolean);

            const visibleImages = Array.from(document.images)
                .map((image) => {
                    const rect = image.getBoundingClientRect();
                    const width = rect.width || image.naturalWidth || 0;
                    const height = rect.height || image.naturalHeight || 0;

                    return {
                        src: resolveUrl(image.currentSrc || image.src),
                        area: width * height,
                    };
                })
                .filter((image) => image.src && image.area > 0)
                .sort((left, right) => right.area - left.area);

            const title = recipeJson?.name || document.title?.trim() || "";
            
            // If we have JSON-LD, we use its fields to avoid noise
            let content = "";
            if (recipeJson) {
                content = `
                RECIPE_JSON_DATA:
                Title: ${recipeJson.name}
                Description: ${recipeJson.description}
                PrepTime: ${recipeJson.prepTime}
                CookTime: ${recipeJson.cookTime}
                Yield: ${recipeJson.recipeYield}
                Ingredients: ${Array.isArray(recipeJson.recipeInstructions) ? JSON.stringify(recipeJson.recipeIngredient) : recipeJson.recipeIngredient}
                Instructions: ${JSON.stringify(recipeJson.recipeInstructions)}
                `.trim();
            } else {
                content = (document.querySelector('main')?.innerText || 
                           document.querySelector('article')?.innerText || 
                           document.body?.innerText || "").trim().substring(0, 15000);
            }

            const thumbnail = recipeJson?.image || (Array.isArray(recipeJson?.image) ? recipeJson.image[0] : null) || imageCandidates[0] || visibleImages[0]?.src || null;

            return {
                title,
                content,
                thumbnail: resolveUrl(typeof thumbnail === 'string' ? thumbnail : thumbnail?.url || null),
            };
        });
        
        return {
            platform: "web",
            description: `TITLE: ${pageData.title}\n\nCONTENT:\n${pageData.content}`.trim(),
            thumbnail: pageData.thumbnail,
        };
    } catch (error) {
        console.error(`[webService] Error extracting ${url}:`, error.message);
        throw new AppError(502, 'BAD_GATEWAY', "Impossible d'extraire les données du site web");
    } finally {
        await browser.close();
    }
}

module.exports = { webService };