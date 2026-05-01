'use strict';

const { OpenAI } = require('openai');
const config = require('./index');

/**
 * Singleton OpenAI client instance.
 * Centralised here so timeout and API key are configured once.
 */
const openaiClient = new OpenAI({
  apiKey: config.openai.apiKey,
  timeout: config.openai.timeout,
  maxRetries: 0, // We handle retries manually per the spec
});

module.exports = openaiClient;
