import { ApiResponse } from "../utils/ApiResponse.js";
import { asyncHandler } from "../utils/AsyncHandler.js";
import { ApiError } from "../utils/ApiError.js";

import ytdl from "@distube/ytdl-core";

export const me = asyncHandler(async (req, res) => {
  const url = "https://www.youtube.com/watch?v=k4jKclPne9I";

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

  return res
    .status(200)
    .json(new ApiResponse(200, payload, "Video info fetched"));
});