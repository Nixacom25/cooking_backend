import { ApiResponse } from "../utils/ApiResponse.js";
import { asyncHandler } from "../utils/AsyncHandler.js";
import { ApiError } from "../utils/ApiError.js";
import { tiktokService } from "../services/tiktok.service.js";
import { instagramService } from "../services/instagram.service.js";
import { openAiService } from "../services/openai.service.js";
import { youtubeService } from "../services/youtube.service.js";
import { webService } from "../services/web.service.js";

export const main = asyncHandler(async (req, res) => {
  const { url } = req.body ?? {};
  if (!url) {
    throw new ApiError(
      400,
      "Missing 'url' in request body"
    );
  }

  let data;
  const isTikTok = /(?:tiktok\.com|vt\.tiktok\.com)/i.test(url);
  const isInstagram = /(?:instagram\.com|instagr\.am)/i.test(url);
  const isYoutube = /(?:youtube\.com|youtu\.be)/i.test(url);

  if (isTikTok) {
    data = await tiktokService(url);
  } else if (isInstagram) {
    data = await instagramService(url);
  } else if (isYoutube) {
    data = await youtubeService(url);
  } else {
    // Handles Facebook, Pinterest, and general recipe websites
    data = await webService(url);
  }

  if (!data) {
    throw new ApiError(500, "Failed to process URL");
  }

  const description = data?.description?.trim?.() || "";
  if (!description) {
    throw new ApiError(422, "No description found to extract recipe from URL");
  }

  const recipe = await openAiService.extractRecipeFromCaption(description);
  const payload = {
    source: data.platform || "web",
    image: data.thumbnail ?? null,
    recipe,
  };

  return res
    .status(200)
    .json(new ApiResponse(200, payload, "Main info fetched"));
});