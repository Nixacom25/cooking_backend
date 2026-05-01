'use strict';

const { AppError } = require('../middleware/errorHandler');
const { InstagramExtractor } = require('@h4md1/instagram-data-extractor');

const INSTAGRAM_HOST_REGEX = /(?:instagram\.com|instagr\.am)/i;

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
 * Fetches post data from Instagram.
 * 
 * @param {string} url 
 * @returns {Promise<object>}
 */
const instagramService = async (url) => {
    const shortcode = getInstagramShortcodeFromUrl(url);
    try {
        const postData = await InstagramExtractor.extractPost(shortcode);
        
        const thumbnail = postData?.media?.find((media) => media?.thumbnailUrl)?.thumbnailUrl ?? postData?.thumbnailUrl ?? null;

        return {
            platform: "instagram",
            description: postData.description || "",
            thumbnail,
        };
    } catch (error) {
        throw new AppError(502, 'BAD_GATEWAY', "Impossible d'extraire les données d'Instagram");
    }
};

module.exports = { instagramService, getInstagramShortcodeFromUrl };