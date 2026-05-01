'use strict';

const ytdl = require('@distube/ytdl-core');
const { AppError } = require('../middleware/errorHandler');

/**
 * Fetches video data from YouTube.
 * 
 * @param {string} url 
 * @returns {Promise<object>}
 */
const youtubeService = async (url) => {
    if (!ytdl.validateURL(url)) {
        throw new AppError(400, 'BAD_REQUEST', "URL YouTube invalide");
    }
    let info;
    try {
        info = await ytdl.getBasicInfo(url);
    } catch (error) {
        throw new AppError(502, 'BAD_GATEWAY', "Impossible de récupérer les informations de la vidéo YouTube");
    }
    if (!info) {
        throw new AppError(404, 'NOT_FOUND', "Aucune information trouvée pour cette vidéo");
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
        description: payload.title + " " + (payload.description ?? ""),
        thumbnail: payload.thumbnail,
    };
};

module.exports = { youtubeService };