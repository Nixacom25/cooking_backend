import axios from "axios";
import { ApiError } from "../utils/ApiError.js";
import ytdl from "@distube/ytdl-core";

export const youtubeService = async (url) => {
    try {
        const oembedUrl = `https://www.youtube.com/oembed?url=${encodeURIComponent(url)}&format=json`;
        const oembedRes = await axios.get(oembedUrl, { timeout: 2500 });
        if (oembedRes.data?.title) {
            return {
                platform: "youtube",
                description: `${oembedRes.data.title} ${oembedRes.data.author_name || ""}`,
                thumbnail: oembedRes.data.thumbnail_url || null,
            };
        }
    } catch (e) {
        // Fallback to ytdl.getBasicInfo below
    }

    if (!ytdl.validateURL(url)) {
        throw new ApiError(400, "Invalid YouTube URL");
    }
    let info;
    try {
        info = await ytdl.getBasicInfo(url);
    } catch (error) {
        throw new ApiError(502, "Unable to fetch YouTube video info");
    }
    if (!info) {
        throw new ApiError(404, "No video info found");
    }
    const payload = {
        title: info.videoDetails.title,
        description: info.videoDetails.description ?? null,
        author:
            typeof info.videoDetails.author === "string"
                ? info.videoDetails.author
                : info.videoDetails.author?.name ?? null,
        views: info.videoDetails.viewCount,
        durationSeconds: info.videoDetails.lengthSeconds,
        thumbnail: info.videoDetails.thumbnails?.[0]?.url ?? null,
    };
    return {
        platform: "youtube",
        description: payload.title + (payload.description ?? ""),
        thumbnail: payload.thumbnail,
    };
};