const axios = require('axios');
const { chromium } = require('playwright');

const INSTAGRAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

async function testAxios(url) {
  try {
    console.log('--- ATTEMPT 1: AXIOS ---');
    const response = await axios.get(url, {
      headers: {
        "user-agent": INSTAGRAM_USER_AGENT,
        "referer": "https://www.instagram.com/",
      },
      timeout: 10000,
    });
    console.log('Axios response status:', response.status);
    const html = response.data;
    
    const ogDescMatch = html.match(/<meta[^>]*property="og:description"[^>]*content="([^"]*)"/);
    const ogImageMatch = html.match(/<meta[^>]*property="og:image"[^>]*content="([^"]*)"/);
    const titleMatch = html.match(/<title>([\s\S]*?)<\/title>/);
    
    console.log('Axios - Title tag:', titleMatch ? titleMatch[1] : 'Not found');
    console.log('Axios - OG Description:', ogDescMatch ? ogDescMatch[1] : 'Not found');
    console.log('Axios - OG Image:', ogImageMatch ? ogImageMatch[1] : 'Not found');
    
    if (ogDescMatch) {
      return { description: ogDescMatch[1], thumbnail: ogImageMatch ? ogImageMatch[1] : null };
    }
  } catch (e) {
    console.error('Axios error:', e.message);
  }
  return null;
}

async function testPlaywright(url) {
  console.log('--- ATTEMPT 2: PLAYWRIGHT ---');
  let browser;
  try {
    browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    const page = await browser.newPage({ userAgent: INSTAGRAM_USER_AGENT });
    
    // Set extra headers or modify cookies if needed, but let's see what happens out of the box
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
    await page.waitForTimeout(3000);
    
    const html = await page.content();
    const ogDescMatch = html.match(/<meta[^>]*property="og:description"[^>]*content="([^"]*)"/);
    const ogImageMatch = html.match(/<meta[^>]*property="og:image"[^>]*content="([^"]*)"/);
    const titleMatch = html.match(/<title>([\s\S]*?)<\/title>/);
    
    console.log('Playwright - Title tag:', titleMatch ? titleMatch[1] : 'Not found');
    console.log('Playwright - OG Description:', ogDescMatch ? ogDescMatch[1] : 'Not found');
    console.log('Playwright - OG Image:', ogImageMatch ? ogImageMatch[1] : 'Not found');
    
    const bodyText = await page.evaluate(() => {
        return (document.querySelector('main')?.innerText || document.body?.innerText || "").trim();
    });
    console.log('Playwright - Body text length:', bodyText.length);
    console.log('Playwright - Body text sample:', bodyText.substring(0, 300));
  } catch (e) {
    console.error('Playwright error:', e.message);
  } finally {
    if (browser) await browser.close();
  }
}

async function run() {
  const url = 'https://www.instagram.com/p/BL2Prubj2uz/';
  await testAxios(url);
  await testPlaywright(url);
}

run();
