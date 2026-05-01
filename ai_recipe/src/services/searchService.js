'use strict';

const { chromium } = require('playwright');
const logger = require('../utils/logger');

/**
 * Performs a web search for recipes and returns a list of candidate URLs.
 * 
 * @param {string} query - Search query
 * @returns {Promise<Array<{title: string, url: string, snippet: string}>>}
 */
async function searchRecipes(query) {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  const page = await browser.newPage();

  try {
    const searchUrl = `https://duckduckgo.com/html/?q=${encodeURIComponent(query + ' recipe')}`;
    logger.info(`Searching recipes on DuckDuckGo: ${query}`);
    
    await page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });

    const results = await page.evaluate(() => {
      const resultElements = Array.from(document.querySelectorAll('.result'));
      return resultElements.slice(0, 10).map(el => {
        const titleLink = el.querySelector('.result__a');
        const snippetEl = el.querySelector('.result__snippet');
        
        return {
          title: titleLink?.innerText?.trim() || '',
          url: titleLink?.href || '',
          snippet: snippetEl?.innerText?.trim() || ''
        };
      }).filter(res => res.url && res.title);
    });

    return results;
  } catch (err) {
    logger.error(`searchRecipes failed: ${err.message}`);
    return [];
  } finally {
    await browser.close();
  }
}

module.exports = { searchRecipes };
