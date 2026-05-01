const axios = require('axios');
const { chromium } = require('playwright');
const { AppError } = require('../middleware/errorHandler');

const TIKTOK_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

const parseTikTokMetaFromHtml = (html) => {
  if (!html) return null;
  
  // Try __UNIVERSAL_DATA_FOR_REHYDRATION__
  const universalMatch = html.match(/<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>([\s\S]*?)<\/script>/);
  if (universalMatch?.[1]) {
    try {
        const universalData = JSON.parse(universalMatch[1]);
        const scope = universalData?.__DEFAULT_SCOPE__;
        
        // Check standard paths
        let item = scope?.["webapp.video-detail"]?.itemInfo?.itemStruct || 
                   scope?.["webapp.video-detail"]?.videoInfo?.video ||
                   scope?.["webapp.video-detail"]?.shareMeta;
                   
        // Search all keys for itemStruct if not found
        if (!item && scope) {
            for (const key in scope) {
                if (scope[key]?.itemInfo?.itemStruct) {
                    item = scope[key].itemInfo.itemStruct;
                    break;
                }
            }
        }

        if (item) {
            return {
                description: item.desc || item.title || "",
                cover: item.video?.cover || item.thumbnailUrl || null,
            };
        }
    } catch (e) {}
  }

  // Try SIGI_STATE
  const sigiMatch = html.match(/<script id="SIGI_STATE"[^>]*>([\s\S]*?)<\/script>/);
  if (sigiMatch?.[1]) {
    try {
        const sigiData = JSON.parse(sigiMatch[1]);
        const itemModule = sigiData?.ItemModule || {};
        const itemId = Object.keys(itemModule)[0];
        const item = itemModule[itemId];

        if (item) {
            return {
                description: item.desc || "",
                cover: item.video?.cover || null,
            };
        }
    } catch (e) {}
  }

  // Final fallback: OG Tags
  const ogDescMatch = html.match(/<meta property="og:description" content="([^"]*)"/);
  const ogImageMatch = html.match(/<meta property="og:image" content="([^"]*)"/);
  
  if (ogDescMatch) {
      return {
          description: ogDescMatch[1] || "",
          cover: ogImageMatch ? ogImageMatch[1] : null,
      };
  }

  return null;
};

const tiktokService = async (url) => {
  if (!/(?:tiktok\.com|v\.tiktok\.com|vm\.tiktok\.com)/i.test(url)) {
    throw new AppError(400, 'BAD_REQUEST', "URL TikTok invalide");
  }

  // Attempt 1: Fast extraction with axios (as in Recipie App)
  try {
    const response = await axios.get(url, {
      headers: {
        "user-agent": TIKTOK_USER_AGENT,
        "referer": "https://www.tiktok.com/",
      },
      timeout: 10000,
      maxRedirects: 10,
    });
    
    const meta = parseTikTokMetaFromHtml(response.data);
    if (meta && meta.description) {
      return {
        platform: "tiktok",
        description: meta.description,
        thumbnail: meta.cover,
      };
    }
  } catch (error) {
    // Silent fail, proceed to Playwright
  }

  // Attempt 2: Robust extraction with Playwright (Fallback)
  let browser;
  try {
    browser = await chromium.launch({ 
        headless: true,
        args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    const page = await browser.newPage({ userAgent: TIKTOK_USER_AGENT });
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
    
    // Wait a bit for JS to populate data
    await page.waitForTimeout(2000);
    
    const html = await page.content();
    const meta = parseTikTokMetaFromHtml(html);
    
    if (!meta || !meta.description) {
        throw new AppError(502, 'BAD_GATEWAY', "Impossible d'extraire les métadonnées de TikTok");
    }

    return {
      platform: "tiktok",
      description: meta.description,
      thumbnail: meta.cover,
    };
  } catch (error) {
    if (error instanceof AppError) throw error;
    throw new AppError(502, 'BAD_GATEWAY', "Échec de l'extraction TikTok via navigateur");
  } finally {
    if (browser) await browser.close();
  }
};

module.exports = { tiktokService };