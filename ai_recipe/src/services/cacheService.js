'use strict';

const { getRedisClient } = require('../config/redis');
const logger = require('../utils/logger');

const INGREDIENT_PREFIX = 'ingredient:';
const RECIPE_PREFIX = 'recipe:';

/**
 * Retrieves a cached value from Redis.
 * Returns null on cache miss or any Redis error (never throws — cache failures
 * must not break the API as per spec section 7).
 *
 * @param {string} key - Full Redis key
 * @returns {Promise<object|null>} Parsed JSON value or null
 */
async function getCache(key) {
  const startedAt = Date.now();
  try {
    const client = getRedisClient();
    logger.event('debug', 'cache.get.start', 'Redis GET started', { key });
    const raw = await client.get(key);
    const durationMs = Date.now() - startedAt;
    if (!raw) {
      logger.event('debug', 'cache.miss', 'Cache miss', { key, duration_ms: durationMs });
      return null;
    }
    logger.event('debug', 'cache.hit', 'Cache hit', { key, duration_ms: durationMs });
    return JSON.parse(raw);
  } catch (err) {
    logger.event('warn', 'cache.get.fail', 'Cache GET failed', {
      key,
      duration_ms: Date.now() - startedAt,
      error: err.message,
    });
    return null;
  }
}

/**
 * Stores a value in Redis with a TTL.
 * Failures are silently swallowed — cache is best-effort.
 *
 * @param {string} key - Full Redis key
 * @param {object} value - Value to serialise and store
 * @param {number} ttlSeconds - Time-to-live in seconds
 */
async function setCache(key, value, ttlSeconds) {
  const startedAt = Date.now();
  try {
    const client = getRedisClient();
    logger.event('debug', 'cache.set.start', 'Redis SET started', { key, ttl_seconds: ttlSeconds });
    await client.set(key, JSON.stringify(value), 'EX', ttlSeconds);
    logger.event('debug', 'cache.set.success', 'Redis SET completed', {
      key,
      ttl_seconds: ttlSeconds,
      duration_ms: Date.now() - startedAt,
    });
  } catch (err) {
    logger.event('warn', 'cache.set.fail', 'Cache SET failed', {
      key,
      ttl_seconds: ttlSeconds,
      duration_ms: Date.now() - startedAt,
      error: err.message,
    });
  }
}

/**
 * Returns the Redis key used for ingredient detection results.
 * Key format: "ingredient:{sha256_of_image_bytes}"
 *
 * @param {string} imageHash - SHA-256 hex digest of raw image bytes
 * @returns {string}
 */
function ingredientCacheKey(imageHash) {
  return `${INGREDIENT_PREFIX}${imageHash}`;
}

/**
 * Returns the Redis key used for generated recipe results.
 * Key format: "recipe:{sha256_of_sorted_lowercased_ingredients}"
 *
 * @param {string} ingredientHash - SHA-256 hex digest of sorted+lowercased ingredient array
 * @returns {string}
 */
function recipeCacheKey(ingredientHash) {
  return `${RECIPE_PREFIX}${ingredientHash}`;
}

module.exports = {
  getCache,
  setCache,
  ingredientCacheKey,
  recipeCacheKey,
};
