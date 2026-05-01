'use strict';

const ytdl = require('@distube/ytdl-core');
const { AppError } = require('../middleware/errorHandler');

/**
 * Test endpoint from Recipie App.
 */
async function me(req, res, next) {
  try {
    const url = "https://www.youtube.com/watch?v=k4jKclPne9I";

    if (!ytdl.validateURL(url)) {
      throw new AppError(400, 'BAD_REQUEST', "Invalid YouTube URL");
    }

    let info;
    try {
      info = await ytdl.getBasicInfo(url);
    } catch (error) {
      throw new AppError(502, 'BAD_GATEWAY', "Unable to fetch YouTube video info");
    }

    if (!info) {
      throw new AppError(404, 'NOT_FOUND', "No video info found");
    }

    const details = info.videoDetails;
    const payload = {
      title: details.title,
      description: details.description ?? null,
      author: typeof details.author === "string" ? details.author : details.author?.name ?? null,
      views: details.viewCount,
      durationSeconds: details.lengthSeconds,
      thumbnail: details.thumbnails?.[0]?.url ?? null,
    };

    return res.status(200).json({
      success: true,
      data: payload,
      message: "Video info fetched"
    });
  } catch (err) {
    next(err);
  }
}

module.exports = { me };