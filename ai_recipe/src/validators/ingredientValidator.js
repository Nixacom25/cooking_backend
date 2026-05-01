'use strict';

const Joi = require('joi');
const config = require('../config/index');
const { AppError } = require('../middleware/errorHandler');
const { normaliseDNA, normaliseSkillLevel, parseTimePreference, parseServingsPreference, normaliseCuisinesLove, normaliseKitchenTools, normaliseCookingGoals } = require('../utils/dna');

/**
 * Checks image magic bytes to confirm the buffer is actually a JPEG, PNG, or WebP file.
 * This prevents corrupt / non-image data from being sent to Cloudinary.
 *
 * @param {Buffer} buf
 * @returns {boolean}
 */
function isValidImageBuffer(buf) {
  if (!buf || buf.length < 12) return false;
  // JPEG: FF D8 FF
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return true;
  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47) return true;
  // WebP: RIFF????WEBP
  if (
    buf.toString('ascii', 0, 4) === 'RIFF' &&
    buf.toString('ascii', 8, 12) === 'WEBP'
  ) return true;
  return false;
}

/**
 * Validates that the uploaded file is present and meets format/size requirements.
 * This runs AFTER multer so req.file is already populated (or missing).
 *
 * @param {import('express').Request} req
 * @param {import('express').Response} _res
 * @param {Function} next
 */
function validateImageUpload(req, _res, next) {
  req.body = req.body || {};
  // If base64, image_url, or ingredients array is provided, skip multipart file validation
  if (req.body.image_base64 || req.body.image_url || (req.body.ingredients && Array.isArray(req.body.ingredients))) {
    // Validate ingredient array items and count
    if (req.body.ingredients && Array.isArray(req.body.ingredients)) {
      const items = req.body.ingredients;
      if (items.length === 0) {
        return next(new AppError(400, 'INVALID_INGREDIENTS', 'ingredients array must not be empty'));
      }
      if (items.length > 50) {
        return next(new AppError(400, 'INVALID_INGREDIENTS', `Too many ingredients (${items.length}). Maximum is 50.`));
      }
      const hasNonString = items.some((item) => typeof item !== 'string');
      if (hasNonString) {
        return next(new AppError(400, 'INVALID_INGREDIENTS', 'All items in the ingredients array must be strings'));
      }
    }
    return next();
  }

  if (!req.file) {
    return next(
      new AppError(
        400,
        'INVALID_IMAGE',
        'No image file provided. Please upload a JPEG, PNG, WEBP image, or provide image_base64 or an ingredients array.'
      )
    );
  }

  // Belt-and-suspenders MIME check (multer already filters, but this guards edge cases)
  if (!config.upload.allowedMimeTypes.includes(req.file.mimetype)) {
    return next(
      new AppError(
        400,
        'INVALID_IMAGE',
        `Unsupported image format: ${req.file.mimetype}. Allowed: JPEG, PNG, WEBP`
      )
    );
  }

  // Size check (multer enforces this via limits, but double-check here with a clearer message)
  if (req.file.size > config.upload.maxFileSizeBytes) {
    return next(
      new AppError(
        400,
        'INVALID_IMAGE',
        `File size (${(req.file.size / (1024 * 1024)).toFixed(2)} MB) exceeds the 10 MB limit`
      )
    );
  }

  // Validate image magic bytes — reject disguised executables, text files, etc.
  if (!isValidImageBuffer(req.file.buffer)) {
    return next(
      new AppError(
        400,
        'INVALID_IMAGE',
        'File content does not match a valid JPEG, PNG, or WebP image. Please upload a genuine image file.'
      )
    );
  }

  next();
}



/**
 * Joi schema for user_preferences (to bound dietary strings).
 */
const userPreferencesSchema = Joi.object({
  allergies: Joi.array().items(Joi.string().max(50)).default([]),
  preferences: Joi.array().items(Joi.string().max(50)).default([]),
  dislikes: Joi.array().items(Joi.string().max(50)).default([]),
  cuisines_love: Joi.alternatives().try(
    Joi.array().items(Joi.string().max(50)),
    Joi.string().max(500)
  ).default([]),
  kitchen_tools: Joi.alternatives().try(
    Joi.array().items(Joi.string().max(50)),
    Joi.string().max(500)
  ).default([]),
  cooking_goals: Joi.alternatives().try(
    Joi.array().items(Joi.string().max(80)),
    Joi.string().max(600)
  ).default([]),
  time_minutes: Joi.any().default(null),
  servings: Joi.any().default(null),
  DNA: Joi.object()
    .pattern(
      Joi.string().trim().min(1).max(50),
      Joi.number().min(0).max(100)
    ),
  skill_level: Joi.alternatives().try(
    Joi.string().valid('total_beginer', 'HomeCook', 'ConfidentCook', 'Advanced Semi Pro'),
    Joi.number().min(0).max(100),
    Joi.object({
      label: Joi.string().valid(null, 'total_beginer', 'HomeCook', 'ConfidentCook', 'Advanced Semi Pro').allow(null),
      percent: Joi.number().min(0).max(100),
    })
  ),
}).default({ allergies: [], preferences: [], dislikes: [], cuisines_love: [], kitchen_tools: [], cooking_goals: [], time_minutes: null, servings: null, DNA: undefined, skill_level: undefined });

/**
 * Validates the user_preferences object (bounded strings, array check).
 *
 * @param {import('express').Request} req
 * @param {import('express').Response} _res
 * @param {Function} next
 */
function validateUserPreferences(req, _res, next) {
  req.body = req.body || {};
  let rawPrefs = req.body.user_preferences;

  // Handle multipart form-data (stringified JSON)
  if (typeof rawPrefs === 'string' && rawPrefs.trim().length > 0) {
    try {
      rawPrefs = JSON.parse(rawPrefs);
    } catch {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'user_preferences must be valid JSON'));
    }
  }

  if (rawPrefs && typeof rawPrefs === 'object' && !Array.isArray(rawPrefs) && rawPrefs.time_minutes == null && rawPrefs.time != null) {
    rawPrefs = { ...rawPrefs, time_minutes: rawPrefs.time };
  }
  if (rawPrefs && typeof rawPrefs === 'object' && !Array.isArray(rawPrefs) && rawPrefs.servings == null && rawPrefs.cooking_for != null) {
    rawPrefs = { ...rawPrefs, servings: rawPrefs.cooking_for };
  }
  if (rawPrefs && typeof rawPrefs === 'object' && !Array.isArray(rawPrefs) && rawPrefs.cuisines_love == null && rawPrefs.cuisines != null) {
    rawPrefs = { ...rawPrefs, cuisines_love: rawPrefs.cuisines };
  }
  if (rawPrefs && typeof rawPrefs === 'object' && !Array.isArray(rawPrefs) && rawPrefs.kitchen_tools == null && rawPrefs.kitchen_equipment != null) {
    rawPrefs = { ...rawPrefs, kitchen_tools: rawPrefs.kitchen_equipment };
  }
  if (rawPrefs && typeof rawPrefs === 'object' && !Array.isArray(rawPrefs) && rawPrefs.cooking_goals == null && rawPrefs.goals != null) {
    rawPrefs = { ...rawPrefs, cooking_goals: rawPrefs.goals };
  }

  const { error, value } = userPreferencesSchema.validate(rawPrefs || {}, {
    stripUnknown: true,
  });

  if (error) {
    const messages = error.details.map((d) => d.message).join('; ');
    return next(new AppError(400, 'INVALID_INGREDIENTS', messages));
  }

  // Swap the raw body with the validated/cleaned preferences
  const parsedTime = parseTimePreference(value.time_minutes);
  if (!parsedTime.valid) {
    return next(new AppError(400, 'INVALID_INGREDIENTS', 'time_minutes must be a minute value, -1, a range object, or a human-readable range like "30-45 min" or "1-2 hours".'));
  }
  const parsedServings = parseServingsPreference(value.servings);
  if (!parsedServings.valid) {
    return next(new AppError(400, 'INVALID_INGREDIENTS', 'servings must be 1, 2, a range like "3-4", an open range like "7+", an object {min,max}, or -1/"it varies".'));
  }

  req.body.user_preferences = {
    ...value,
    DNA: normaliseDNA(value.DNA),
    skill_level: normaliseSkillLevel(value.skill_level),
    cuisines_love: normaliseCuisinesLove(value.cuisines_love),
    kitchen_tools: normaliseKitchenTools(value.kitchen_tools),
    cooking_goals: normaliseCookingGoals(value.cooking_goals),
    time_minutes: parsedTime.value,
    servings: parsedServings.value,
  };
  next();
}

/**
 * Validates the full POST /api/analyze request body.
 *
 * Writes `req.validatedBody` with:
 *   { user_id, user_preferences, manual_ingredients, ingredients }
 *
 * - user_id              — optional string
 * - user_preferences     — validated/normalised (same rules as validateUserPreferences)
 * - manual_ingredients   — optional string[] (≤30, each ≤100 chars), sanitised;
 *                          merged with photo-detected ingredients before recipe generation
 * - ingredients          — optional string[] (≤30); used as text-only fallback when
 *                          no image or base64 is provided
 */
function validateAnalyzeBody(req, _res, next) {
  req.body = req.body || {};

  // ── user_id ────────────────────────────────────────────────────────────────
  const userId = req.body.user_id ? String(req.body.user_id).trim() : null;

  // ── user_preferences (same alias + Joi pipeline as validateUserPreferences) ─
  let rawPrefs = req.body.user_preferences;
  if (typeof rawPrefs === 'string' && rawPrefs.trim().length > 0) {
    try { rawPrefs = JSON.parse(rawPrefs); } catch {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'user_preferences must be valid JSON'));
    }
  }
  if (rawPrefs && typeof rawPrefs === 'object' && !Array.isArray(rawPrefs)) {
    if (rawPrefs.time_minutes == null && rawPrefs.time != null)
      rawPrefs = { ...rawPrefs, time_minutes: rawPrefs.time };
    if (rawPrefs.servings == null && rawPrefs.cooking_for != null)
      rawPrefs = { ...rawPrefs, servings: rawPrefs.cooking_for };
    if (rawPrefs.cuisines_love == null && rawPrefs.cuisines != null)
      rawPrefs = { ...rawPrefs, cuisines_love: rawPrefs.cuisines };
    if (rawPrefs.kitchen_tools == null && rawPrefs.kitchen_equipment != null)
      rawPrefs = { ...rawPrefs, kitchen_tools: rawPrefs.kitchen_equipment };
    if (rawPrefs.cooking_goals == null && rawPrefs.goals != null)
      rawPrefs = { ...rawPrefs, cooking_goals: rawPrefs.goals };
  }
  const { error: prefsError, value: prefsValue } = userPreferencesSchema.validate(rawPrefs || {}, { stripUnknown: true });
  if (prefsError) {
    const msg = prefsError.details.map((d) => d.message).join('; ');
    return next(new AppError(400, 'INVALID_INGREDIENTS', msg));
  }
  const parsedTime = parseTimePreference(prefsValue.time_minutes);
  if (!parsedTime.valid) {
    return next(new AppError(400, 'INVALID_INGREDIENTS',
      'time_minutes must be a minute value, -1, a range object, or a human-readable range like "30-45 min" or "1-2 hours".'));
  }
  const parsedServings = parseServingsPreference(prefsValue.servings);
  if (!parsedServings.valid) {
    return next(new AppError(400, 'INVALID_INGREDIENTS',
      'servings must be 1, 2, a range like "3-4", an open range like "7+", an object {min,max}, or -1/"it varies".'));
  }
  const validatedPrefs = {
    ...prefsValue,
    DNA: normaliseDNA(prefsValue.DNA),
    skill_level: normaliseSkillLevel(prefsValue.skill_level),
    cuisines_love: normaliseCuisinesLove(prefsValue.cuisines_love),
    kitchen_tools: normaliseKitchenTools(prefsValue.kitchen_tools),
    cooking_goals: normaliseCookingGoals(prefsValue.cooking_goals),
    time_minutes: parsedTime.value,
    servings: parsedServings.value,
  };

  // ── manual_ingredients (optional array merged with detected items) ──────────
  let manualIngredients = [];
  if (req.body.manual_ingredients != null) {
    let raw = req.body.manual_ingredients;
    // multipart sends it as a JSON string
    if (typeof raw === 'string') {
      try { raw = JSON.parse(raw); } catch { raw = [raw]; }
    }
    if (!Array.isArray(raw)) {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'manual_ingredients must be an array of strings'));
    }
    if (raw.length > 30) {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'manual_ingredients: maximum 30 items allowed'));
    }
    if (raw.some((i) => typeof i !== 'string')) {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'manual_ingredients: all items must be strings'));
    }
    manualIngredients = require('../utils/hash').sanitiseIngredients(raw);
  }

  // ── ingredients (text-only fallback — skip Vision when provided) ───────────
  let textIngredients = [];
  if (req.body.ingredients != null) {
    let raw = req.body.ingredients;
    if (typeof raw === 'string') {
      try { raw = JSON.parse(raw); } catch { raw = [raw]; }
    }
    if (!Array.isArray(raw)) {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'ingredients must be an array of strings'));
    }
    if (raw.length === 0) {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'ingredients array must not be empty'));
    }
    if (raw.length > 30) {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'ingredients: maximum 30 items allowed'));
    }
    if (raw.some((i) => typeof i !== 'string')) {
      return next(new AppError(400, 'INVALID_INGREDIENTS', 'ingredients: all items must be strings'));
    }
    textIngredients = require('../utils/hash').sanitiseIngredients(raw);
    if (textIngredients.length === 0) {
      return next(new AppError(400, 'INVALID_INGREDIENTS',
        'All ingredient names were empty after sanitisation. Please provide valid ingredient names.'));
    }
  }

  req.validatedBody = {
    user_id: userId,
    user_preferences: validatedPrefs,
    manual_ingredients: manualIngredients,
    ingredients: textIngredients,
  };

  next();
}

module.exports = { validateImageUpload, validateUserPreferences, validateAnalyzeBody, isValidImageBuffer };
