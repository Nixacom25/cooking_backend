'use strict';

const rateLimit = require('express-rate-limit');
const config = require('../config/index');

/**
 * General API rate limiter — applied to ALL routes.
 * skipFailedRequests: true means 4xx responses don't count against the limit,
 * so running validation tests never exhausts the window.
 */
const apiRateLimiter = rateLimit({
  windowMs: config.rateLimit.windowMs,
  max: config.env === 'production' ? config.rateLimit.maxRequests : 1000,
  standardHeaders: 'draft-7', // draft-7 or true
  legacyHeaders: false,
  skipFailedRequests: false,  // Track everything for global limiter to ensure headers are always present
  handler(_req, res) {
    return res.status(429).json({
      success: false,
      error: {
        code: 'RATE_LIMIT_EXCEEDED',
        message: 'Too many requests. Please slow down and try again later.',
      },
    });
  },
});

/**
 * Stricter limiter for AI-heavy endpoints to protect OpenAI API costs.
 * Production: 20 req/15min. Development: 200 req/15min for easy testing.
 * skipFailedRequests: true — validation errors (400) don't count.
 */
const aiEndpointLimiter = rateLimit({
  windowMs: config.rateLimit.windowMs,
  max: config.rateLimit.aiMaxRequests,
  standardHeaders: true,
  legacyHeaders: false,
  skipFailedRequests: true,   // validation rejections don't burn quota
  handler(_req, res) {
    return res.status(429).json({
      success: false,
      error: {
        code: 'RATE_LIMIT_EXCEEDED',
        message: 'AI endpoint rate limit exceeded. Please wait before making more requests.',
      },
    });
  },
});

module.exports = { apiRateLimiter, aiEndpointLimiter };
