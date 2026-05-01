'use strict';

const crypto = require('crypto');

/**
 * Generates a SHA-256 hash of a Buffer (image bytes).
 * Used as the Redis cache key for ingredient detection results.
 *
 * @param {Buffer} buffer - Raw image bytes
 * @returns {string} Hex-encoded SHA-256 digest
 */
function hashBuffer(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

/**
 * Generates a deterministic SHA-256 hash from a list of ingredient strings
 * and user preferences (allergies, diet).
 * Strings are lowercased and sorted before hashing to ensure cache key stability.
 *
 * @param {string[]} ingredients - Array of ingredient names
 * @param {object} userPreferences - { allergies: string[], preferences: string[] }
 * @returns {string} Hex-encoded SHA-256 digest
 */
function hashIngredients(ingredients, userPreferences = {}) {
  const normalised = ingredients
    .map((i) => i.trim().toLowerCase())
    .sort()
    .join(',');

  // Deterministically sort keys recursively so nested objects (e.g. DNA) affect cache keys.
  const stable = (value) => {
    if (Array.isArray(value)) return value.map(stable);
    if (value && typeof value === 'object') {
      return Object.keys(value)
        .sort()
        .reduce((acc, key) => {
          acc[key] = stable(value[key]);
          return acc;
        }, {});
    }
    return value;
  };

  const prefsString = JSON.stringify(stable(userPreferences));
  const combined = `${normalised}|${prefsString}`;

  return crypto.createHash('sha256').update(combined).digest('hex');
}

/**
 * Sanitises a single ingredient string by:
 * - Trimming whitespace
 * - Removing characters that are not letters, digits, spaces, hyphens, or apostrophes
 * - Collapsing multiple spaces
 * - Truncating to 100 characters
 *
 * @param {string} ingredient - Raw ingredient string
 * @returns {string} Sanitised ingredient string
 */
function sanitiseIngredient(ingredient) {
  return ingredient
    .trim()
    .replace(/[^a-zA-Z0-9 '\-]/g, '')
    .replace(/\s+/g, ' ')
    .substring(0, 80);
}

/**
 * Sanitises an array of ingredient strings.
 * Filters out any strings that become empty after sanitisation.
 *
 * @param {string[]} ingredients
 * @returns {string[]}
 */
function sanitiseIngredients(ingredients) {
  return ingredients.map(sanitiseIngredient).filter(Boolean);
}

module.exports = {
  hashBuffer,
  hashIngredients,
  sanitiseIngredient,
  sanitiseIngredients,
};
