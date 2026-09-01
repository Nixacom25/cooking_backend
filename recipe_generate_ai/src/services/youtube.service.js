import axios from "axios";
import { ApiError } from "../utils/ApiError.js";
import ytdl from "@distube/ytdl-core";
import { chromium } from "playwright";

export const youtubeService = async (url) => {
    // 1. Try ytdl.getBasicInfo first
    try {
        if (ytdl.validateURL(url)) {
            const info = await ytdl.getBasicInfo(url);
            if (info?.videoDetails) {
                const title = info.videoDetails.title || "";
                const description = info.videoDetails.description || "";
                const thumbnail = info.videoDetails.thumbnails?.[0]?.url || null;
                if (title || description) {
                    return {
                        platform: "youtube",
                        description: `${title}\n${description}`.trim(),
                        thumbnail,
                    };
                }
            }
        }
    } catch (e) {
        console.warn("⚠️ ytdl-core failed, attempting oembed / playwright fallback:", e.message);
    }

    // 2. Try YouTube oembed API
    try {
        const oembedUrl = `https://www.youtube.com/oembed?url=${encodeURIComponent(url)}&format=json`;
        const oembedRes = await axios.get(oembedUrl, { timeout: 3000 });
        if (oembedRes.data?.title) {
            const title = oembedRes.data.title;
            const author = oembedRes.data.author_name || "";
            const thumbnail = oembedRes.data.thumbnail_url || null;
            return {
                platform: "youtube",
                description: `${title} by ${author}`,
                thumbnail,
            };
        }
    } catch (e) {}

    // 3. Fallback to Playwright page extraction
    let browser;
    try {
        browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
        const page = await browser.newPage();
        await page.goto(url, { waitUntil: "domcontentloaded", timeout: 20000 });
        const data = await page.evaluate(() => {
            const title = document.title || "";
            const desc = document.querySelector('meta[name="description"]')?.getAttribute("content") ||
                         document.querySelector('#description')?.innerText || "";
            const thumbnail = document.querySelector('meta[property="og:image"]')?.getAttribute("content") || null;
            return { title, desc, thumbnail };
        });
        return {
            platform: "youtube",
            description: `${data.title}\n${data.desc}`.trim(),
            thumbnail: data.thumbnail,
        };
    } catch (err) {
        throw new ApiError(502, `Failed to extract YouTube content: ${err.message}`);
    } finally {
        if (browser) await browser.close();
    }
};