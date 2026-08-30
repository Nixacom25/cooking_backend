import axios from "axios";
import { chromium } from "playwright";

const USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

export async function webService(url) {
    // 1. Fast HTTP fetch with Axios (~150ms-300ms)
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

            if (combinedDesc.length > 10) {
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

    // 2. Fallback to Playwright if static fetch fails
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();
    try {
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 8000 });
        const pageData = await page.evaluate(() => {
            const getMetaContent = (...selectors) => {
                for (const selector of selectors) {
                    const element = document.querySelector(selector);
                    const content = element?.getAttribute("content")?.trim();
                    if (content) return content;
                }
                return null;
            };
            const title = document.title?.trim() || "";
            const content = getMetaContent('meta[property="og:description"]', 'meta[name="description"]') || document.body?.innerText?.trim() || "";
            const thumbnail = getMetaContent('meta[property="og:image"]', 'meta[name="twitter:image"]');
            return { title, content, thumbnail };
        });
        const platform = /facebook\.com|fb\.watch/i.test(url) ? "facebook" : "web";
        return {
            platform,
            description: `${pageData.title} ${pageData.content}`.trim(),
            thumbnail: pageData.thumbnail,
        };
    } finally {
        await browser.close();
    }
}