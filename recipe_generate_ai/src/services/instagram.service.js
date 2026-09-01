import { ApiError } from "../utils/ApiError.js";
import { InstagramExtractor } from '@h4md1/instagram-data-extractor';
import { chromium } from 'playwright';

const INSTAGRAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const INSTAGRAM_HOST_REGEX = /(?:instagram\.com|instagr\.am)/i;

const unescapeHtml = (str) => {
    if (!str || typeof str !== 'string') return str;
    let decoded = str;
    while (decoded.includes('&amp;')) {
        decoded = decoded.replace(/&amp;/g, '&');
    }
    return decoded
        .replace(/&quot;/g, '"')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&#39;/g, "'")
        .replace(/&apos;/g, "'");
};

export const getInstagramShortcodeFromUrl = (url) => {
    if (!url || typeof url !== "string") {
        throw new ApiError(400, "Instagram URL is required");
    }

    let parsedUrl;
    try {
        parsedUrl = new URL(url);
    } catch {
        throw new ApiError(400, "Invalid Instagram URL format");
    }

    if (!INSTAGRAM_HOST_REGEX.test(parsedUrl.hostname)) {
        throw new ApiError(400, "Invalid Instagram URL");
    }

    const segments = parsedUrl.pathname.split("/").filter(Boolean);
    const normalizedSegments = segments.map((segment) => segment.toLowerCase());
    const contentTypeIndex = normalizedSegments.findIndex((segment) =>
        ["p", "reel", "reels", "tv"].includes(segment)
    );
    const shortcode = contentTypeIndex >= 0 ? segments[contentTypeIndex + 1] : undefined;

    if (!shortcode) {
        throw new ApiError(400, "Could not extract shortcode from Instagram URL");
    }

    return shortcode;
};

const parseInstagramMetaFromHtml = (html) => {
    if (!html) return null;
    
    const ogDescMatch = html.match(/<meta[^>]*property="og:description"[^>]*content="([^"]*)"/i) ||
                        html.match(/<meta[^>]*name="description"[^>]*content="([^"]*)"/i) ||
                        html.match(/<meta[^>]*content="([^"]*)"[^>]*property="og:description"/i);
    const ogImageMatch = html.match(/<meta[^>]*property="og:image"[^>]*content="([^"]*)"/i) ||
                         html.match(/<meta[^>]*content="([^"]*)"[^>]*property="og:image"/i);
    
    let description = ogDescMatch ? ogDescMatch[1] : null;
    if (description) description = unescapeHtml(description);

    let cover = ogImageMatch ? ogImageMatch[1] : null;
    if (cover) cover = unescapeHtml(cover);
    
    return { description, cover };
};

export const instagramService = async (url) => {
    const shortcode = getInstagramShortcodeFromUrl(url);

    // Attempt 1: Fast API extraction via InstagramExtractor
    try {
        console.log(`[Instagram] Fast API extraction for shortcode: ${shortcode}`);
        const postData = await InstagramExtractor.extractPost(shortcode);
        const thumbnail = postData?.media?.find((media) => media?.thumbnailUrl)?.thumbnailUrl ?? postData?.thumbnailUrl ?? null;

        if (postData && postData.description && postData.description.trim().length > 5) {
            console.log(`[Instagram] Fast API extraction successful!`);
            return {
                platform: "instagram",
                description: unescapeHtml(postData.description),
                thumbnail: unescapeHtml(thumbnail),
            };
        }
    } catch (error) {
        console.warn(`[Instagram] Fast API extraction failed: ${error.message}`);
    }

    // Attempt 2: Playwright headless browser fallback
    console.log(`[Instagram] Fallback to Playwright browser extraction for: ${url}`);
    let browser;
    try {
        browser = await chromium.launch({
            headless: true,
            args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-blink-features=AutomationControlled']
        });
        const context = await browser.newContext({ userAgent: INSTAGRAM_USER_AGENT });
        const page = await context.newPage();
        
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 25000 });
        await page.waitForTimeout(2000);
        
        const html = await page.content();
        let meta = parseInstagramMetaFromHtml(html);

        if (!meta || !meta.description || meta.description.trim().length < 5) {
            const bodyText = await page.evaluate(() => {
                return (document.querySelector('main')?.innerText || document.body?.innerText || "").trim();
            });
            if (bodyText && bodyText.length > 20) {
                meta = {
                    description: bodyText.substring(0, 8000),
                    cover: meta?.cover || null
                };
                console.log(`[Instagram] Fallback to raw page text successful.`);
            }
        }

        if (!meta || !meta.description) {
            throw new ApiError(502, "Could not extract content from Instagram URL");
        }

        return {
            platform: "instagram",
            description: unescapeHtml(meta.description),
            thumbnail: unescapeHtml(meta.cover),
        };
    } catch (error) {
        if (error instanceof ApiError) throw error;
        throw new ApiError(502, `Failed to extract Instagram content: ${error.message}`);
    } finally {
        if (browser) await browser.close();
    }
};