'use strict';

const multer = require('multer');
const config = require('../config/index');
const { AppError } = require('./errorHandler');

/**
 * Multer configuration for ingredient image uploads.
 *
 * - Files are stored in memory as Buffers so we can:
 *   1. Hash the raw bytes for the Redis cache key.
 *   2. Stream the buffer directly to Cloudinary.
 * - Format and size validation happens both here (multer) and in the
 *   ingredientValidator middleware for belt-and-suspenders safety.
 */
const upload = multer({
  storage: multer.memoryStorage(),

  limits: {
    fileSize: config.upload.maxFileSizeBytes, // 10 MB
    files: 1,
  },

  fileFilter(_req, file, cb) {
    if (config.upload.allowedMimeTypes.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(
        new AppError(
          400,
          'INVALID_IMAGE',
          `Unsupported file type: ${file.mimetype}. Allowed: JPEG, PNG, WEBP`
        )
      );
    }
  },
});

/**
 * Express error handler specifically for Multer upload errors.
 * Converts Multer-specific error codes into our standard error envelope.
 *
 * @param {Error} err
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {Function} next
 */
function handleUploadError(err, req, res, next) {
  if (err instanceof multer.MulterError) {
    if (err.code === 'LIMIT_FILE_SIZE') {
      return next(
        new AppError(
          400,
          'INVALID_IMAGE',
          `File too large. Maximum allowed size is ${config.upload.maxFileSizeBytes / (1024 * 1024)} MB`
        )
      );
    }
    return next(new AppError(400, 'INVALID_IMAGE', err.message));
  }
  next(err);
}

module.exports = { upload, handleUploadError };
