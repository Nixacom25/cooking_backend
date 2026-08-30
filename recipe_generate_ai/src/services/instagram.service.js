import { ApiError } from "../utils/ApiError.js";
import { InstagramExtractor } from '@h4md1/instagram-data-extractor';
const INSTAGRAM_HOST_REGEX = /(?:instagram\.com|instagr\.am)/i;

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



export const instagramService = async (url) => {
    const shortcode = getInstagramShortcodeFromUrl(url);
    const postData = await InstagramExtractor.extractPost(shortcode);
    console.log('postdata ', postData);

    const thumbnail = postData?.media?.find((media) => media?.thumbnailUrl)?.thumbnailUrl ?? postData?.thumbnailUrl ?? null;

    return {
        platform: "instagram",
        // ...postData,
        // url,
        description: postData.description,
        thumbnail,
    };
};