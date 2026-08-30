import axios from "axios";
import { ApiError } from "../utils/ApiError.js";

const TIKTOK_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
const parseTikTokMetaFromHtml = (html) => {
  const universalMatch = html.match(
    /<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>([\s\S]*?)<\/script>/
  );
  if (universalMatch?.[1]) {
    const universalData = JSON.parse(universalMatch[1]);
    const item =
      universalData?.__DEFAULT_SCOPE__?.["webapp.video-detail"]?.itemInfo
        ?.itemStruct ?? null;

    if (item) {
      return {
        id: item.id ?? null,
        description: item.desc ?? null,
        author: item.author?.nickname ?? item.author?.uniqueId ?? null,
        createTime: item.createTime ?? null,
        duration: item.video?.duration ?? null,
        cover: item.video?.cover ?? null,
        videoUrl: item.video?.playAddr ?? null,
        stats: {
          views: item.stats?.playCount ?? null,
          likes: item.stats?.diggCount ?? null,
          comments: item.stats?.commentCount ?? null,
          shares: item.stats?.shareCount ?? null,
        },
      };
    }
  }

  const sigiMatch = html.match(
    /<script id="SIGI_STATE"[^>]*>([\s\S]*?)<\/script>/
  );
  if (!sigiMatch?.[1]) {
    return null;
  }

  const sigiData = JSON.parse(sigiMatch[1]);
  const itemModule = sigiData?.ItemModule ?? {};
  const itemId = Object.keys(itemModule)[0];
  const item = itemModule[itemId];

  if (!item) {
    return null;
  }

  return {
    id: item.id ?? null,
    description: item.desc ?? null,
    author: item.author ?? null,
    createTime: item.createTime ?? null,
    duration: item.video?.duration ?? null,
    cover: item.video?.cover ?? null,
    videoUrl: item.video?.playAddr ?? null,
    stats: {
      views: item.stats?.playCount ?? null,
      likes: item.stats?.diggCount ?? null,
      comments: item.stats?.commentCount ?? null,
      shares: item.stats?.shareCount ?? null,
    },
  };
};

export const tiktokService = async (url) => {
  if (!/tiktok\.com/i.test(url)) {
    throw new ApiError(400, "Invalid TikTok URL");
  }
  let html;
  let resolvedUrl = url;
  try {
    const response = await axios.get(url, {
      headers: {
        "user-agent": TIKTOK_USER_AGENT,
        referer: "https://www.tiktok.com/",
      },
      maxRedirects: 5,
    });
    html = response.data;
    resolvedUrl = response?.request?.res?.responseUrl || url;
  } catch (error) {
    throw new ApiError(502, "Failed to fetch TikTok page");
  }

  const meta = parseTikTokMetaFromHtml(html);
  if (!meta) {
    throw new ApiError(502, "Unable to parse TikTok video metadata");
  }
  return {
    platform: "tiktok",
    // url: resolvedUrl,
    description: meta.description,
    thumbnail: meta.cover,
    // ...meta,
  };
};