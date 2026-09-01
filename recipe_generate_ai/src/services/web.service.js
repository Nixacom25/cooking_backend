import axios from "axios";
import { chromium } from "playwright";
import { ApiError } from "../utils/ApiError.js";

const USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

export async function webService(url) {
    // 1. Fast HTTP fetch with Axios (~200ms)
    try {
        const response = await axios.get(url, {
            headers: {
                "User-Agent": USER_AGENT,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language": "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7",
            },
            timeout: 3500,
            maxRedirects: 5,
        });

        const html = response.data;
        if (typeof html === "string" && html.length > 0) {
            const getMeta = (prop) => {
                const match = html.match(new RegExp(`<meta[^>]+(?:property|name)=["']${prop}["'][^>]+content=["']([^"']+)["']`, "i")) ||
                              html.match(new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${prop}["']`, "i"));
                return match ? match[1].trim() : null;
            };

            const title = getMeta("og:title") || getMeta("twitter:title") || (html.match(/<title[^>]*>([^<]+)<\/title>/i)?.[1]?.trim() || "");
            const description = getMeta("og:description") || getMeta("description") || getMeta("twitter:description") || "";
            const thumbnail = getMeta("og:image") || getMeta("og:image:secure_url") || getMeta("twitter:image") || null;

            const combinedDesc = `${title} ${description}`.trim();

            if (combinedDesc.length > 20) {
                const platform = /facebook\.com|fb\.watch/i.test(url) ? "facebook" 
                               : /pinterest\.com/i.test(url) ? "pinterest"
                               : "web";
                return {
                    platform,
                    description: combinedDesc,
                    thumbnail,
                };
            }
        }
    } catch (err) {
        console.warn("⚠️ Fast HTTP fetch failed for URL, falling back to Playwright:", err.message);
    }

    // 2. Fallback to Playwright with JSON-LD and page content extraction
    let browser;
    try {
        browser = await chromium.launch({
            headless: true,
            args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-blink-features=AutomationControlled']
        });
        const context = await browser.newContext({ userAgent: USER_AGENT, viewport: { width: 1280, height: 720 } });
        const page = await context.newPage();

        await page.addInitScript(() => {
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
        });

        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 25000 });
        await page.waitForTimeout(2000);

        const pageData = await page.evaluate(() => {
            const findRecipeJsonLd = () => {
                const scripts = Array.from(document.querySelectorAll('script[type="application/ld+json"]'));
                for (const script of scripts) {
                    try {
                        const json = JSON.parse(script.innerText);
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

            const title = recipeJson?.name || document.title?.trim() || "";
            
            let content = "";
            if (recipeJson) {
                content = `
                RECIPE_JSON_DATA:
                Title: ${recipeJson.name}
                Description: ${recipeJson.description}
                PrepTime: ${recipeJson.prepTime}
                CookTime: ${recipeJson.cookTime}
                Yield: ${recipeJson.recipeYield}
                Ingredients: ${Array.isArray(recipeJson.recipeIngredient) ? JSON.stringify(recipeJson.recipeIngredient) : recipeJson.recipeIngredient}
                Instructions: ${JSON.stringify(recipeJson.recipeInstructions)}
                `.trim();
            } else {
                content = getMetaContent('meta[property="og:description"]', 'meta[name="description"]') || 
                          (document.querySelector('main')?.innerText || document.body?.innerText || "").trim().substring(0, 8000);
            }

            const thumbnail = recipeJson?.image || (Array.isArray(recipeJson?.image) ? recipeJson.image[0] : null) || 
                              getMetaContent('meta[property="og:image"]', 'meta[name="twitter:image"]');

            return { title, content, thumbnail: typeof thumbnail === 'string' ? thumbnail : thumbnail?.url || null };
        });

        const platform = /facebook\.com|fb\.watch/i.test(url) ? "facebook" : "web";
        return {
            platform,
            description: `${pageData.title}\n\n${pageData.content}`.trim(),
            thumbnail: pageData.thumbnail,
        };
    } catch (error) {
        throw new ApiError(502, `Failed to extract web content: ${error.message}`);
    } finally {
        if (browser) await browser.close();
    }
}