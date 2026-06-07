'use strict';

const logger = require('../utils/logger');

/**
 * Custom application error class.
 * Carries an HTTP status code and a machine-readable error code
 * matching the specs error envelope format.
 */
class AppError extends Error {
  /**
   * @param {number} statusCode - HTTP status code (400, 422, 500, etc.)
   * @param {string} code - Machine-readable error code (e.g. 'INVALID_IMAGE')
   * @param {string} message - Human-readable description for logging
   */
  constructor(statusCode, code, message) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.isOperational = true; // Distinguishes expected app errors from programming bugs
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * Global Express error handler.
 *
 * Returns the standard error envelope defined in section 9 of the spec:
 * {
 *   "success": false,
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "message": "Human-readable description"
 *   }
 * }
 *
 * Stack traces are never sent to the client.
 *
 * @param {Error} err
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {Function} _next - Required 4-arg signature for Express to recognise error handler
 */
function errorHandler(err, req, res, _next) {
  // Determine if this is a known operational error or an unexpected programming error
  const isOperational = err.isOperational === true;
  const statusCode = err.statusCode || 500;
  const code = err.code || 'INTERNAL_SERVER_ERROR';

  // Log full details internally (including stack for bugs)
  if (isOperational) {
    logger.event('warn', 'request.error', `[${code}] ${err.message}`, {
      code,
      status_code: statusCode,
      path: req.path,
      method: req.method,
      ip: req.ip,
    });
  } else {
    logger.event('error', 'request.error.unexpected', 'Unexpected error', {
      code,
      status_code: statusCode,
      message: err.message,
      stack: err.stack,
      path: req.path,
      method: req.method,
      ip: req.ip,
    });
  }

  // Return sanitised error to client — never expose stack or internal details
  return res.status(statusCode).json({
    success: false,
    error: {
      code,
      message: isOperational
        ? err.message
        : 'An unexpected error occurred. Please try again later.',
    },
  });
}

/**
 * Catches unhandled promise rejections inside Express route handlers.
 * Wrap async route handlers with this to avoid having to try/catch every handler.
 *
 * @param {Function} fn - Async Express route handler
 * @returns {Function} Wrapped handler that forwards errors to next()
 */
function asyncHandler(fn) {
  return (req, res, next) => {
    return Promise.resolve(fn(req, res, next)).catch(next);
  };
}

module.exports = { AppError, errorHandler, asyncHandler };
