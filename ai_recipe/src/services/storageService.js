'use strict';

const streamifier = require('streamifier');
const crypto = require('crypto');
const cloudinary = require('../config/storage');
const config = require('../config/index');
const logger = require('../utils/logger');
const { AppError } = require('../middleware/errorHandler');

/**
 * Uploads an image buffer to Cloudinary.
 *
 * Implementation notes:
 * - We use upload_stream so the raw buffer from multer's memory storage is streamed
 *   directly to Cloudinary without writing a temp file to disk.
 * - Cloudinary returns a secure HTTPS URL which is passed to the vision model.
 * - If the upload fails, we throw STORAGE_ERROR and do NOT proceed to the AI model
 *   (per spec section 9.2).
 *
 * @param {Buffer} fileBuffer - Raw image bytes from multer
 * @param {string} originalName - Original filename (used to derive public_id)
 * @returns {Promise<string>} Cloudinary secure URL
 */
async function uploadImage(fileBuffer, originalName) {
  const startedAt = Date.now();
  return new Promise((resolve, reject) => {
    // Derive a secure UUID public ID
    const publicId = crypto.randomUUID();
    logger.event('info', 'storage.upload.start', 'Cloudinary upload started', {
      original_name: originalName,
      public_id: publicId,
      size_bytes: fileBuffer ? fileBuffer.length : 0,
    });

    const uploadStream = cloudinary.uploader.upload_stream(
      {
        folder: config.cloudinary.folder,
        public_id: publicId,
        overwrite: false,
        resource_type: 'image',
        // Cloudinary will auto-detect format; we've already validated it client-side
      },
      (error, result) => {
        if (error) {
          logger.event('error', 'storage.upload.fail', 'Cloudinary upload failed', {
            original_name: originalName,
            public_id: publicId,
            duration_ms: Date.now() - startedAt,
            error: error.message,
          });
          return reject(
            new AppError(
              500,
              'STORAGE_ERROR',
              `Cloud storage upload failed: ${error.message}`
            )
          );
        }
        logger.event('info', 'storage.upload.success', 'Cloudinary upload completed', {
          original_name: originalName,
          public_id: publicId,
          duration_ms: Date.now() - startedAt,
          secure_url: result.secure_url,
        });
        resolve(result.secure_url);
      }
    );

    // Pipe the in-memory buffer into the Cloudinary upload stream
    streamifier.createReadStream(fileBuffer).pipe(uploadStream);
  });
}

module.exports = { uploadImage };
