'use strict';

const { AppError } = require('../middleware/errorHandler');
const { InstagramExtractor } = require('@h4md1/instagram-data-extractor');
const { chromium } = require('playwright');

const INSTAGRAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const INSTAGRAM_HOST_REGEX = /(?:instagram\.com|instagr\.am)/i;

/**
 * Decodes all HTML entities including multiple/nested encodings.
 * 
 * @param {string} str 
 * @returns {string} decoded string
 */
const unescapeHtml = (str) => {
    if (!str || typeof str !== 'string') return str;
    let decoded = str;
    // Loop to resolve potential nested &amp; entities (e.g. &amp;amp;)
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

/**
 * Extracts the shortcode from an Instagram URL.
 * 
 * @param {string} url 
 * @returns {string} shortcode
 */
const getInstagramShortcodeFromUrl = (url) => {
    if (!url || typeof url !== "string") {
        throw new AppError(400, 'BAD_REQUEST', "L'URL Instagram est requise");
    }

    let parsedUrl;
    try {
        parsedUrl = new URL(url);
    } catch {
        throw new AppError(400, 'BAD_REQUEST', "Format d'URL Instagram invalide");
    }

    if (!INSTAGRAM_HOST_REGEX.test(parsedUrl.hostname)) {
        throw new AppError(400, 'BAD_REQUEST', "URL Instagram invalide");
    }

    const segments = parsedUrl.pathname.split("/").filter(Boolean);
    const normalizedSegments = segments.map((segment) => segment.toLowerCase());
    const contentTypeIndex = normalizedSegments.findIndex((segment) =>
        ["p", "reel", "reels", "tv"].includes(segment)
    );
    const shortcode = contentTypeIndex >= 0 ? segments[contentTypeIndex + 1] : undefined;

    if (!shortcode) {
        throw new AppError(400, 'BAD_REQUEST', "Impossible d'extraire le shortcode de l'URL Instagram");
    }

    return shortcode;
};

/**
 * Parses OG metadata from Instagram page HTML.
 * 
 * @param {string} html 
 * @returns {object|null}
 */
const parseInstagramMetaFromHtml = (html) => {
    if (!html) return null;
    
    // Extract og:description
    const ogDescMatch = html.match(/<meta[^>]*property="og:description"[^>]*content="([^"]*)"/) ||
                       html.match(/<meta[^>]*name="description"[^>]*content="([^"]*)"/);
    // Extract og:image
    const ogImageMatch = html.match(/<meta[^>]*property="og:image"[^>]*content="([^"]*)"/);
    
    let description = ogDescMatch ? ogDescMatch[1] : null;
    if (description) {
        description = unescapeHtml(description);
    }

    let cover = ogImageMatch ? ogImageMatch[1] : null;
    if (cover) {
        cover = unescapeHtml(cover);
    }
    
    return {
        description,
        cover
    };
};

/**
 * Fetches post data from Instagram.
 * 
 * @param {string} url 
 * @returns {Promise<object>}
 */
const instagramService = async (url) => {
    const shortcode = getInstagramShortcodeFromUrl(url);

    // Attempt 1: Fast API extraction using InstagramExtractor
    try {
        console.log(`[Instagram] Attempting fast API extraction for shortcode: ${shortcode}`);
        const postData = await InstagramExtractor.extractPost(shortcode);
        const thumbnail = postData?.media?.find((media) => media?.thumbnailUrl)?.thumbnailUrl ?? postData?.thumbnailUrl ?? null;

        if (postData && postData.description) {
            console.log(`[Instagram] Fast API extraction successful!`);
            return {
                platform: "instagram",
                description: unescapeHtml(postData.description),
                thumbnail: unescapeHtml(thumbnail),
            };
        }
        console.log(`[Instagram] Fast API extraction returned empty description.`);
    } catch (error) {
        console.log(`[Instagram] Fast API extraction failed: ${error.message}`);
    }

    // Attempt 2: Headless browser extraction with Playwright (fallback)
    console.log(`[Instagram] Attempting browser extraction for URL: ${url}`);
    let browser;
    try {
        browser = await chromium.launch({
            headless: true,
            args: ['--no-sandbox', '--disable-setuid-sandbox']
        });
        const page = await browser.newPage({ userAgent: INSTAGRAM_USER_AGENT });
        
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
        await page.waitForTimeout(3000);
        
        const html = await page.content();
        let meta = parseInstagramMetaFromHtml(html);

        // Fallback to visible body text if description metadata tag is missing
        if (!meta || !meta.description) {
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
            throw new AppError(502, 'BAD_GATEWAY', "Impossible d'extraire les données d'Instagram.");
        }

        return {
            platform: "instagram",
            description: unescapeHtml(meta.description),
            thumbnail: unescapeHtml(meta.cover),
        };
    } catch (error) {
        if (error instanceof AppError) throw error;
        throw new AppError(502, 'BAD_GATEWAY', `Impossible d'extraire les données d'Instagram via le navigateur: ${error.message}`);
    } finally {
        if (browser) await browser.close();
    }
};

module.exports = { instagramService, getInstagramShortcodeFromUrl };