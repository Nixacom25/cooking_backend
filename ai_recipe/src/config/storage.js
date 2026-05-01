'use strict';

const cloudinary = require('cloudinary').v2;
const config = require('./index');
const logger = require('../utils/logger');

/**
 * Initialises the Cloudinary SDK with credentials from environment variables.
 * Cloudinary is used as the cloud image storage provider.
 */
cloudinary.config({
  cloud_name: config.cloudinary.cloudName,
  api_key: config.cloudinary.apiKey,
  api_secret: config.cloudinary.apiSecret,
  secure: true,
});

logger.info('Cloudinary storage client initialised');

module.exports = cloudinary;
