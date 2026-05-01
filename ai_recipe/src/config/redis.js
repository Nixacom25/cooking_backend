'use strict';

const Redis = require('ioredis');
const config = require('./index');
const logger = require('../utils/logger');

let client = null;

/**
 * Creates and returns a singleton Redis client using ioredis.
 * Connection events are logged for observability.
 *
 * @returns {Redis} ioredis client instance
 */
function getRedisClient() {
  if (client) return client;

  logger.event('info', 'db.redis.connect.start', 'Redis client initialization started');
  client = new Redis(config.redis.url, {
    maxRetriesPerRequest: 3,
    enableReadyCheck: true,
    connectTimeout: 10000,
    disconnectTimeout: 2000,
    retryStrategy(times) {
      const delay = Math.min(times * 200, 10000);
      logger.event('warn', 'db.redis.retry', 'Redis reconnect scheduled', {
        attempt: times,
        delay_ms: delay,
      });
      return delay;
    },
  });

  client.on('connect', () => logger.event('info', 'db.redis.connect.success', 'Redis client connected'));
  client.on('ready', () => logger.event('info', 'db.redis.ready', 'Redis client ready'));
  client.on('error', (err) => logger.event('error', 'db.redis.error', 'Redis client error', { error: err.message }));
  client.on('close', () => logger.event('warn', 'db.redis.close', 'Redis connection closed'));
  client.on('reconnecting', () => logger.event('warn', 'db.redis.reconnecting', 'Redis reconnecting'));

  return client;
}

/**
 * Gracefully closes the Redis connection.
 */
async function disconnectRedis() {
  if (client) {
    const startedAt = Date.now();
    await client.quit();
    client = null;
    logger.event('info', 'db.redis.disconnect.success', 'Redis connection closed', {
      duration_ms: Date.now() - startedAt,
    });
  }
}

module.exports = { getRedisClient, disconnectRedis };
