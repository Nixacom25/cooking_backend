'use strict';

const Joi = require('joi');
const { AppError } = require('../middleware/errorHandler');
const { sanitiseIngredients } = require('../utils/hash');
const { normaliseDNA, normaliseSkillLevel, parseTimePreference, parseServingsPreference, normaliseCuisinesLove, normaliseKitchenTools, normaliseCookingGoals } = require('../utils/dna');

/**
 * Joi schema for the POST /api/recipes/generate request body.
 *
 * Validation rules from spec section 4.2:
 * - ingredients must be a non-empty array of strings
 * - Minimum 1 ingredient, maximum 30 ingredients
 * - Each ingredient string: max 100 characters
 */
const generateRecipesSchema = Joi.object({
  ingredients: Joi.array()
    .items(
      Joi.string()
        .max(80)
        .required()
        .messages({
          'string.max': 'Each ingredient must be 80 characters or fewer',
          'string.empty': 'Ingredient strings must not be empty',
        })
    )
    .min(1)
    .max(30)
    .required()
    .messages({
      'array.min': 'At least 1 ingredient is required',
      'array.max': 'A maximum of 30 ingredients are allowed',
      'array.base': '"ingredients" must be an array of strings',
      'any.required': '"ingredients" field is required',
    }),
  /**
   * Optional user preferences (allergies/dietary) from AI API spec section 3.
   * These are forwarded to the AI prompt to filter recipes accordingly.
   */
  user_preferences: Joi.object({
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
  }).default({ allergies: [], preferences: [], dislikes: [], cuisines_love: [], kitchen_tools: [], cooking_goals: [], time_minutes: null, servings: null, DNA: undefined, skill_level: undefined }),
});

/**
 * Express middleware that validates and sanitises the recipe generation request body.
 * Attaches sanitised data back to `req` for downstream handlers.
 *
 * @param {import('express').Request} req
 * @param {import('express').Response} _res
 * @param {Function} next
 */
function validateGenerateRecipes(req, _res, next) {
  if (req.body && typeof req.body === 'object' && req.body.user_preferences && typeof req.body.user_preferences === 'object' && !Array.isArray(req.body.user_preferences) && req.body.user_preferences.time_minutes == null && req.body.user_preferences.time != null) {
    req.body = {
      ...req.body,
      user_preferences: {
        ...req.body.user_preferences,
        time_minutes: req.body.user_preferences.time,
      },
    };
  }
  if (req.body && typeof req.body === 'object' && req.body.user_preferences && typeof req.body.user_preferences === 'object' && !Array.isArray(req.body.user_preferences) && req.body.user_preferences.servings == null && req.body.user_preferences.cooking_for != null) {
    req.body = {
      ...req.body,
      user_preferences: {
        ...req.body.user_preferences,
        servings: req.body.user_preferences.cooking_for,
      },
    };
  }
  if (req.body && typeof req.body === 'object' && req.body.user_preferences && typeof req.body.user_preferences === 'object' && !Array.isArray(req.body.user_preferences) && req.body.user_preferences.cuisines_love == null && req.body.user_preferences.cuisines != null) {
    req.body = {
      ...req.body,
      user_preferences: {
        ...req.body.user_preferences,
        cuisines_love: req.body.user_preferences.cuisines,
      },
    };
  }
  if (req.body && typeof req.body === 'object' && req.body.user_preferences && typeof req.body.user_preferences === 'object' && !Array.isArray(req.body.user_preferences) && req.body.user_preferences.kitchen_tools == null && req.body.user_preferences.kitchen_equipment != null) {
    req.body = {
      ...req.body,
      user_preferences: {
        ...req.body.user_preferences,
        kitchen_tools: req.body.user_preferences.kitchen_equipment,
      },
    };
  }
  if (req.body && typeof req.body === 'object' && req.body.user_preferences && typeof req.body.user_preferences === 'object' && !Array.isArray(req.body.user_preferences) && req.body.user_preferences.cooking_goals == null && req.body.user_preferences.goals != null) {
    req.body = {
      ...req.body,
      user_preferences: {
        ...req.body.user_preferences,
        cooking_goals: req.body.user_preferences.goals,
      },
    };
  }

  const { error, value } = generateRecipesSchema.validate(req.body, {
    abortEarly: false,
    stripUnknown: true,
  });

  if (error) {
    const messages = error.details.map((d) => d.message).join('; ');
    return next(new AppError(400, 'INVALID_INGREDIENTS', messages));
  }

  const parsedTime = parseTimePreference(value.user_preferences.time_minutes);
  if (!parsedTime.valid) {
    return next(new AppError(400, 'INVALID_INGREDIENTS', 'time_minutes must be a minute value, -1, a range object, or a human-readable range like "30-45 min" or "1-2 hours".'));
  }
  const parsedServings = parseServingsPreference(value.user_preferences.servings);
  if (!parsedServings.valid) {
    return next(new AppError(400, 'INVALID_INGREDIENTS', 'servings must be 1, 2, a range like "3-4", an open range like "7+", an object {min,max}, or -1/"it varies".'));
  }

  // Sanitise ingredient strings after schema validation
  const sanitised = sanitiseIngredients(value.ingredients);
  if (sanitised.length === 0) {
    return next(
      new AppError(
        400,
        'INVALID_INGREDIENTS',
        'All ingredients were empty after sanitisation. Please provide valid ingredient names.'
      )
    );
  }

  // Attach validated & sanitised data to request
  req.validatedBody = {
    ingredients: sanitised,
    user_preferences: {
      ...value.user_preferences,
      DNA: normaliseDNA(value.user_preferences.DNA),
      skill_level: normaliseSkillLevel(value.user_preferences.skill_level),
      cuisines_love: normaliseCuisinesLove(value.user_preferences.cuisines_love),
      kitchen_tools: normaliseKitchenTools(value.user_preferences.kitchen_tools),
      cooking_goals: normaliseCookingGoals(value.user_preferences.cooking_goals),
      time_minutes: parsedTime.value,
      servings: parsedServings.value,
    },
  };

  next();
}

/**
 * Middleware for validating /api/recipes/suggest (onboarding suggestions).
 * Allows empty ingredients.
 */
function validateSuggestRecipes(req, _res, next) {
  // Same preference mapping logic as validateGenerateRecipes
  if (req.body && typeof req.body === 'object' && req.body.user_preferences && typeof req.body.user_preferences === 'object') {
    const p = req.body.user_preferences;
    if (p.time_minutes == null && p.time != null) p.time_minutes = p.time;
    if (p.servings == null && p.cooking_for != null) p.servings = p.cooking_for;
    if (p.cuisines_love == null && p.cuisines != null) p.cuisines_love = p.cuisines;
    if (p.kitchen_tools == null && p.kitchen_equipment != null) p.kitchen_tools = p.kitchen_equipment;
    if (p.cooking_goals == null && p.goals != null) p.cooking_goals = p.goals;
  }

  const suggestSchema = Joi.object({
    ingredients: Joi.array().items(Joi.string().max(80)).default([]),
    recipe_pool: Joi.array().items(Joi.string().max(100)).optional(),
    user_preferences: generateRecipesSchema.extract('user_preferences')
  });

  const { error, value } = suggestSchema.validate(req.body, {
    abortEarly: false,
    stripUnknown: true,
  });

  if (error) {
    return next(new AppError(400, 'INVALID_INGREDIENTS', error.details.map((d) => d.message).join('; ')));
  }

  const parsedTime = parseTimePreference(value.user_preferences.time_minutes);
  const parsedServings = parseServingsPreference(value.user_preferences.servings);

  req.validatedBody = {
    ingredients: [],
    user_preferences: {
      ...value.user_preferences,
      DNA: normaliseDNA(value.user_preferences.DNA),
      skill_level: normaliseSkillLevel(value.user_preferences.skill_level),
      cuisines_love: normaliseCuisinesLove(value.user_preferences.cuisines_love),
      kitchen_tools: normaliseKitchenTools(value.user_preferences.kitchen_tools),
      cooking_goals: normaliseCookingGoals(value.user_preferences.cooking_goals),
      time_minutes: parsedTime.value,
      servings: parsedServings.value,
    },
  };

  next();
}

module.exports = { validateGenerateRecipes, validateSuggestRecipes };
