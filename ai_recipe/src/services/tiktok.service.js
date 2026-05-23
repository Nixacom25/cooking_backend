const axios = require('axios');
const { chromium } = require('playwright');
const { AppError } = require('../middleware/errorHandler');

const TIKTOK_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1";

const parseTikTokMetaFromHtml = (html) => {
  if (!html) return null;
  
  // Try __UNIVERSAL_DATA_FOR_REHYDRATION__
  const universalMatch = html.match(/<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>([\s\S]*?)<\/script>/);
  if (universalMatch?.[1]) {
    try {
        const universalData = JSON.parse(universalMatch[1]);
        const scope = universalData?.__DEFAULT_SCOPE__;
        
        let item = scope?.["webapp.video-detail"]?.itemInfo?.itemStruct || 
                   scope?.["webapp.video-detail"]?.videoInfo?.video ||
                   scope?.["webapp.video-detail"]?.shareMeta ||
                   scope?.["webapp.reflow.video.detail"]?.itemInfo?.itemStruct;
                   
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

  // Try __INITIAL_STATE__ (Newer TikTok layout)
  const initialMatch = html.match(/<script id="__INITIAL_STATE__"[^>]*>([\s\S]*?)<\/script>/);
  if (initialMatch?.[1]) {
    try {
        const initialData = JSON.parse(initialMatch[1]);
        const item = initialData?.sharingMeta?.video || initialData?.seoProps?.metaParams;
        if (item) {
            return {
                description: item.title || item.description || "",
                cover: item.poster || null,
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
  const ogTitleMatch = html.match(/<meta property="og:title" content="([^"]*)"/);
  const ogImageMatch = html.match(/<meta property="og:image" content="([^"]*)"/);
  
  if (ogDescMatch || ogTitleMatch) {
      return {
          description: (ogDescMatch ? ogDescMatch[1] : ogTitleMatch[1]) || "",
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
    console.log(`[TikTok] Attempting fast extraction for: ${url}`);
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
      console.log(`[TikTok] Fast extraction successful: ${meta.description.substring(0, 50)}...`);
      return {
        platform: "tiktok",
        description: meta.description,
        thumbnail: meta.cover,
      };
    }
    console.log(`[TikTok] Fast extraction failed to find metadata.`);
  } catch (error) {
    console.log(`[TikTok] Fast extraction error: ${error.message}`);
    // Silent fail, proceed to Playwright
  }

  // Attempt 2: Robust extraction with Playwright (Fallback)
  console.log(`[TikTok] Attempting browser extraction for: ${url}`);
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
    let meta = parseTikTokMetaFromHtml(html);
    
    // Fallback: If metadata parsing fails, grab the visible text from the page
    if (!meta || !meta.description) {
        const bodyText = await page.evaluate(() => {
            return (document.querySelector('main')?.innerText || document.body?.innerText || "").trim();
        });
        
        if (bodyText && bodyText.length > 20) {
            meta = {
                description: bodyText.substring(0, 8000),
                cover: null
            };
            console.log(`[TikTok] Fallback to raw page text successful.`);
        }
    }
    
    if (!meta || !meta.description) {
        throw new AppError(502, 'BAD_GATEWAY', "Failed to extract TikTok metadata.");
    }

    return {
      platform: "tiktok",
      description: meta.description,
      thumbnail: meta.cover,
    };
  } catch (error) {
    if (error instanceof AppError) throw error;
    throw new AppError(502, 'BAD_GATEWAY', "Failed to extract TikTok via browser.");
  } finally {
    if (browser) await browser.close();
  }
};

module.exports = { tiktokService };