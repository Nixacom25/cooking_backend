'use strict';

/**
 * AI Service — OpenAI integration for ingredient detection and recipe generation.
 *
 * This module encapsulates all prompt engineering, model calls, JSON parsing,
 * schema validation, and retry logic as specified in sections 5 and 9 of the
 * backend requirements document.
 *
 * Retry policy (per spec):
 *  - On any AI model error → retry once with identical parameters → 500 AI_SERVICE_ERROR
 *  - On schema validation failure → retry once with AMENDED prompt → 422 SCHEMA_VALIDATION_FAILED
 */

const openaiClient = require('../config/openai');
const config = require('../config/index');
const logger = require('../utils/logger');
const { AppError } = require('../middleware/errorHandler');
const { uploadImage } = require('./storageService');
const { normaliseDNA, normaliseSkillLevel, normaliseTimePreference, normaliseServingsPreference, normaliseCuisinesLove, normaliseKitchenTools, normaliseCookingGoals } = require('../utils/dna');
const { sanitiseIngredient } = require('../utils/hash');

async function timedAiCall(eventBase, model, operation, fn, details = {}) {
  const startedAt = Date.now();
  logger.event('info', `${eventBase}.start`, `${operation} started`, {
    model,
    ...details,
  });
  try {
    const result = await fn();
    logger.event('info', `${eventBase}.success`, `${operation} succeeded`, {
      model,
      duration_ms: Date.now() - startedAt,
      ...details,
    });
    return result;
  } catch (err) {
    logger.event('warn', `${eventBase}.fail`, `${operation} failed`, {
      model,
      duration_ms: Date.now() - startedAt,
      error: err.message,
      ...details,
    });
    throw err;
  }
}

// ---------------------------------------------------------------------------
// INGREDIENT DETECTION — configured OpenAI vision model
// ---------------------------------------------------------------------------

/**
 * System prompt for the ingredient detection vision model.
 * Instructs the model to return ONLY valid JSON.
 */
/**
 * Builds the system prompt for the ingredient detection vision model.
 * Instructs the model to return ONLY valid JSON and categorize ingredients based on dietary restrictions.
 *
 * @param {string[]} allergies - User allergy list
 * @param {string[]} preferences - User dietary preference list
 */
function buildIngredientSystemPrompt(allergies = [], preferences = [], dislikes = []) {
  // dislikes are intentionally ignored here — disliked items are still detected
  // (they're in the fridge) and only avoided during recipe generation
  const dietaryContext = allergies.length > 0 || preferences.length > 0
    ? `The user has the following dietary restrictions. Allergies: ${allergies.join(', ')}. Preferences: ${preferences.join(', ')}.
You must categorize the detected ingredients into "allowed_ingredients" (safe for the user) and "restricted_ingredients" (unsafe based on their allergies/preferences).`
    : `The user has no known dietary restrictions. Categorize all detected ingredients as "allowed_ingredients" and leave "restricted_ingredients" empty.`;

  return `You are a culinary AI assistant specialised in identifying food ingredients from images of fridges, pantries, and ingredient collections.
Your task is to analyse the provided image and return a JSON object listing ALL visible food ingredients — these will be used to generate recipe suggestions for what the user CAN COOK.
You MUST return ONLY valid JSON. Do NOT include any prose, markdown, code fences, or explanations.

${dietaryContext}

Required JSON format:
{
  "allowed_ingredients": [
    { "name": "<ingredient name, lowercase>", "icon": "<emoji>", "quantity": "<quantity with unit if visible, otherwise null>", "confidence": <float 0.0 to 1.0> }
  ],
  "restricted_ingredients": [
    { "name": "<ingredient name, lowercase>", "icon": "<emoji>", "reason": "<why it violates the restrictions>" }
  ]
}

Rules:
- Identify ALL food ingredients visible in the image (fridge contents, pantry items, produce, meats, dairy, condiments, spices, etc.).
- Only include actual food/cooking ingredients. Ignore packaging labels, non-food items, and containers.
- Use common, recognisable ingredient names in English, lowercase (e.g. "chicken breast" not "poultry", "cheddar cheese" not "dairy product").
- Be thorough — the more ingredients identified, the better the recipe suggestions will be.
- Confidence must be a float between 0.0 and 1.0.
- If you cannot identify any food ingredients, return empty arrays.
- Return ONLY the JSON object — nothing else.`;
}

/**
 * Sends an image URL to the configured OpenAI vision model and returns the parsed ingredient list.
 * Implements one automatic retry on any model or parsing failure.
 *
 * @param {string} imageUrl - Publicly accessible image URL (from Cloudinary)
 * @param {object} userPreferences - { allergies: string[], preferences: string[] }
 * @returns {Promise<object>} { allowed_ingredients, restricted_ingredients }
 * @throws {AppError} 422 NO_INGREDIENTS_FOUND | 500 AI_SERVICE_ERROR
 */
async function detectIngredientsFromImage(imageUrl, userPreferences = {}) {
  const { allergies = [], preferences = [], dislikes = [] } = userPreferences;
  let lastError;

  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const response = await timedAiCall(
        'ai.vision.detect',
        config.openai.visionModel,
        'Ingredient detection call',
        () =>
          openaiClient.chat.completions.create({
            model: config.openai.visionModel,
            response_format: { type: 'json_object' },
            messages: [
              {
                role: 'system',
                content: buildIngredientSystemPrompt(allergies, preferences, dislikes),
              },
              {
                role: 'user',
                content: [
                  {
                    type: 'image_url',
                    image_url: { url: imageUrl, detail: 'high' },
                  },
                  {
                    type: 'text',
                    text: 'Please analyse this image and list ALL food ingredients you can see. This could be a photo of a fridge, pantry, kitchen counter, or a collection of ingredients. Be thorough — identify every food item so we can suggest recipes.',
                  },
                ],
              },
            ],
            max_completion_tokens: 1024,
          }),
        { attempt, image_url: imageUrl }
      );

      const raw = response.choices[0]?.message?.content;
      if (!raw) throw new Error('Empty response from vision model');

      const parsed = JSON.parse(raw);
      const validated = validateIngredientSchema(parsed);

      logger.event('info', 'ai.vision.detect.parsed', 'Ingredient detection parsed', {
        attempt,
        allowed_count: validated.allowed_ingredients.length,
        restricted_count: validated.restricted_ingredients.length,
      });
      return validated;
    } catch (err) {
      lastError = err;
      logger.warn(
        `Ingredient detection attempt ${attempt} failed: ${err.message}`
      );
      // Retry once; if second attempt also fails we fall through to error handling
    }
  }

  // Both attempts failed
  if (lastError instanceof AppError) throw lastError;
  throw new AppError(500, 'AI_SERVICE_ERROR', `Vision model failed: ${lastError.message}`);
}

/**
 * Validates the raw AI response against the ingredient schema.
 * Throws a typed error if validation fails so the caller can retry.
 *
 * @param {object} parsed - Parsed JSON from the model
 * @returns {Array<{name: string, confidence: number}>}
 */
function validateIngredientSchema(parsed) {
  if (!parsed || !Array.isArray(parsed.allowed_ingredients) || !Array.isArray(parsed.restricted_ingredients)) {
    throw new Error('Invalid ingredient schema: "allowed_ingredients" or "restricted_ingredients" array missing');
  }

  // Filter entries that have the required fields; drop malformed ones
  const validAllowed = parsed.allowed_ingredients.filter(
    (item) =>
      typeof item.name === 'string' &&
      item.name.trim().length > 0 &&
      typeof item.confidence === 'number' &&
      item.confidence >= 0 &&
      item.confidence <= 1
  );

  const validRestricted = parsed.restricted_ingredients.filter(
    (item) =>
      typeof (item.name || item.ingredient) === 'string' &&
      (item.name || item.ingredient).trim().length > 0 &&
      typeof item.reason === 'string' &&
      item.reason.trim().length > 0
  );

  if (validAllowed.length === 0 && validRestricted.length === 0 && (parsed.allowed_ingredients.length > 0 || parsed.restricted_ingredients.length > 0)) {
    throw new Error('No ingredient entries passed schema validation');
  }

  const sanitiseName = (raw) => sanitiseIngredient(raw.trim().toLowerCase());

  return {
    allowed_ingredients: validAllowed.map((item) => ({
      name: sanitiseName(item.name),
      icon: String(item.icon || '').trim(),
      quantity: item.quantity ? String(item.quantity).trim() : null,
      confidence: Math.round(item.confidence * 100) / 100,
    })).filter((item) => item.name.length > 0),
    restricted_ingredients: validRestricted.map((item) => ({
      name: sanitiseName(item.name || item.ingredient),
      icon: String(item.icon || '').trim(),
      reason: item.reason.trim(),
    })).filter((item) => item.name.length > 0),
    detected_ingredients: [
      ...validAllowed.map((item) => ({
        name: sanitiseName(item.name),
        icon: String(item.icon || '').trim(),
        quantity: item.quantity ? String(item.quantity).trim() : null,
        confidence: Math.round(item.confidence * 100) / 100,
        status: 'allowed',
      })),
      ...validRestricted.map((item) => ({
        name: sanitiseName(item.name || item.ingredient),
        icon: String(item.icon || '').trim(),
        reason: item.reason.trim(),
        status: 'restricted',
      }))
    ].filter((item) => item.name.length > 0),
    ingredients: [
      ...validAllowed.map((item) => ({
        name: sanitiseName(item.name),
        icon: String(item.icon || '').trim(),
        quantity: item.quantity ? String(item.quantity).trim() : null,
        confidence: Math.round(item.confidence * 100) / 100,
      })),
      ...validRestricted.map((item) => ({
        name: sanitiseName(item.name || item.ingredient),
        icon: String(item.icon || '').trim(),
        confidence: 1.0,
      }))
    ].filter((item) => item.name.length > 0),
  };
}

/**
 * Fast text-only endpoint to categorize an existing list of ingredients 
 * based on dietary restrictions using the configured OpenAI generation model.
 */
async function filterPreDetectedIngredients(ingredientList, userPreferences = {}) {
  const { allergies = [], preferences = [] } = userPreferences;

  // Fast path: no restrictions = everything allowed
  if (allergies.length === 0 && preferences.length === 0) {
    const list = ingredientList.map((name) => ({
      name: String(name).trim().toLowerCase(),
      confidence: 1.0
    }));
    return {
      allowed_ingredients: list,
      restricted_ingredients: [],
      detected_ingredients: list.map(item => ({ ...item, status: 'allowed' })),
      ingredients: list,
      _synthetic_confidence: true // Marker for internal auditing
    };
  }

  const systemPrompt = `You are a culinary AI assistant. You will be given a list of food ingredients.
The user has the following dietary restrictions. Allergies: ${allergies.join(', ')}. Preferences: ${preferences.join(', ')}.
You must categorize the provided ingredients into "allowed_ingredients" (safe for the user) and "restricted_ingredients" (unsafe).

Required JSON format:
{
  "allowed_ingredients": [
    { "name": "<ingredient name, lowercase>", "icon": "<emoji>", "quantity": "<visible quantity or null>", "confidence": 1.0 }
  ],
  "restricted_ingredients": [
    { "name": "<ingredient name, lowercase>", "icon": "<emoji>", "reason": "<why it violates the restrictions>" }
  ]
}
Return ONLY valid JSON.`;

  for (let attempt = 1; attempt <= 2; attempt++) {
    try {
      const response = await timedAiCall(
        'ai.filter.detected-list',
        config.openai.generationModel,
        'Pre-detected list filtering call',
        () =>
          openaiClient.chat.completions.create({
            model: config.openai.generationModel,
            response_format: { type: 'json_object' },
            messages: [
              { role: 'system', content: systemPrompt },
              { role: 'user', content: `Categorize these ingredients: ${ingredientList.join(', ')}` },
            ],
          }),
        { attempt, ingredient_count: ingredientList.length }
      );

      const raw = response.choices[0]?.message?.content;
      const parsed = JSON.parse(raw);
      return validateIngredientSchema(parsed);
    } catch (err) {
      if (attempt === 2) throw new AppError(500, 'AI_SERVICE_ERROR', `Filtering failed: ${err.message}`);
    }
  }
}

// ---------------------------------------------------------------------------
// RECIPE GENERATION — configured OpenAI generation model
// ---------------------------------------------------------------------------

/**
 * Builds the system prompt for recipe generation.
 * Includes strict JSON schema, count constraints, and user preferences.
 *
 * @param {string[]} allergies - User allergy list
 * @param {string[]} preferences - User dietary preference list
 * @param {string[]} dislikes - User disliked ingredients list
 * @param {object} DNA - Taste DNA profile
 * @param {object} skill_level - Cooking skill level { label: string|null, percent: number }
 * @param {string[]} cuisines_love - Preferred cuisine names/slugs
 * @param {string[]} kitchen_tools - Available kitchen tools
 * @param {string[]} cooking_goals - User cooking goals ordered by priority
 * @param {null|number|object} time_minutes - Target cooking time in minutes or range
 * @param {null|object} servings - Preferred servings range
 * @returns {string}
 */
function buildRecipeSystemPrompt(allergies = [], preferences = [], dislikes = [], DNA = {}, skill_level = {}, cuisines_love = [], kitchen_tools = [], cooking_goals = [], time_minutes = null, servings = null, system_instructions = null) {
  const allergyClause =
    allergies.length > 0
      ? `CRITICAL — the user is allergic to: ${allergies.join(', ')}. Do NOT include these ingredients in ANY recipe.`
      : 'The user has no known allergies.';

  const prefClause =
    preferences.length > 0
      ? `User dietary preferences: ${preferences.join(', ')}. Prioritise recipes that respect these preferences.`
      : '';

  const dislikesClause =
    dislikes.length > 0
      ? `The user dislikes: ${dislikes.join(', ')}. Avoid using these as main ingredients in recipes where possible.`
      : '';

  const normCuisinesLove = normaliseCuisinesLove(cuisines_love);
  const cuisinesClause =
    normCuisinesLove.length > 0
      ? `Cuisine preferences: strongly prioritise flavors, techniques, and dish styles from: ${normCuisinesLove.join(', ')}.`
      : '';

  const normKitchenTools = normaliseKitchenTools(kitchen_tools);
  const kitchenToolsClause =
    normKitchenTools.length > 0
      ? `Available kitchen tools: ${normKitchenTools.join(', ')}. Keep methods compatible with these tools.`
      : '';

  const normCookingGoals = normaliseCookingGoals(cooking_goals);
  const cookingGoalsClause =
    normCookingGoals.length > 0
      ? `User cooking goals (priority order): ${normCookingGoals.join(', ')}.`
      : '';

  const dnaProfile = normaliseDNA(DNA);
  const dnaClause = `Taste DNA profile (0-100 preference weights): ${Object.entries(dnaProfile)
    .map(([k, v]) => `${k}: ${v}`)
    .join(', ')}. Use higher scores to bias recipe style.`;

  const norm_skill = normaliseSkillLevel(skill_level);
  let skillLevelClause = '';
  if (norm_skill.percent <= 20) {
    skillLevelClause = `Cooking skill level: BEGINNER (${norm_skill.percent}%)`;
  } else if (norm_skill.percent <= 50) {
    skillLevelClause = `Cooking skill level: HOME COOK (${norm_skill.percent}%)`;
  } else if (norm_skill.percent <= 75) {
    skillLevelClause = `Cooking skill level: CONFIDENT COOK (${norm_skill.percent}%)`;
  } else {
    skillLevelClause = `Cooking skill level: ADVANCED (${norm_skill.percent}%)`;
  }

  const normTime = normaliseTimePreference(time_minutes);
  let timeClause = 'Cooking time preference: any time is acceptable.';
  if (typeof normTime === 'number') {
    timeClause = `Cooking time preference: target recipes that take about ${normTime} minutes or less.`;
  } else if (normTime && typeof normTime === 'object') {
    timeClause = `Cooking time preference: target recipes between ${normTime.min} and ${normTime.max} minutes.`;
  }

  const normServings = normaliseServingsPreference(servings);
  let servingsClause = 'Servings preference: user will adjust portions as needed.';
  if (normServings && typeof normServings === 'object') {
    if (normServings.max === null) {
      servingsClause = `Servings preference: recipes should be suitable for ${normServings.min}+ people.`;
    } else if (normServings.min === normServings.max) {
      servingsClause = `Servings preference: recipes should target ${normServings.min} serving(s).`;
    } else {
      servingsClause = `Servings preference: recipes should target ${normServings.min}-${normServings.max} servings.`;
    }
  }

  return `You are a professional chef-level recipe generation engine.

You will receive:
1. A list of available ingredients
2. A user cooking profile:
[User Profile:
${allergyClause}
${prefClause}
${dislikesClause}
${cuisinesClause}
${kitchenToolsClause}
${cookingGoalsClause}
${dnaClause}
${skillLevelClause}
${timeClause}
${servingsClause}]

Your task is to generate between 1 and 6 DISTINCT, realistic, creative, and highly detailed recipes using ONLY the provided ingredients.

ABSOLUTE INGREDIENT RULE:
- You MUST use ONLY ingredients explicitly provided by the user.
- NEVER add any ingredient whatsoever unless it exists in the provided ingredient list.
- This includes:
  - oil
  - salt
  - pepper
  - butter
  - water
  - garlic
  - onion
  - sauces
  - flour
  - eggs
  - spices
  - herbs
  - garnishes
  - condiments
  - broth
  - sugar
  - milk
  - cream
  - or ANY hidden/default cooking ingredient.
- Every ingredient mentioned anywhere in the response MUST exist in the provided ingredient list.
- This rule applies to:
  - recipe titles
  - ingredients arrays
  - recipe steps
  - tips
  - image prompts
  - descriptions
  - equipment usage references
  - ALL text output.

RECIPE VALIDATION RULE:
Before returning the final JSON:
- Verify every recipe is physically achievable using ONLY the provided ingredients.
- Verify every ingredient mentioned exists in the user's ingredient list.
- Remove any recipe containing hallucinated or missing ingredients.
- Ensure steps logically match available ingredients.
- Ensure recipes are structurally realistic and edible.

RECIPE DIVERSITY REQUIREMENT:
Every recipe MUST feel meaningfully different from the others.

Avoid minor variations of the same dish.

Prioritize diversity across:
- baked
- fried
- skillet
- crispy
- layered
- stuffed
- handheld
- casserole
- bowl-style
- patties
- croquettes
- melts
- soups
- wraps
- creamy
- toasted
- pan-seared
- compacted
- sliced
- stacked

Each recipe should differ in:
- cooking technique
- texture
- presentation
- structure
- eating experience
- plating style
- mouthfeel

CREATIVE COOKING REQUIREMENT:
Think like a professional chef working with limited ingredients.

Use:
- shaping
- layering
- crisping
- melting
- baking
- folding
- compacting
- frying
- stuffing
- temperature contrast
- texture contrast
- presentation

to maximize recipe variety.

Do NOT rely solely on ingredient differences to differentiate recipes.

=== EXAMPLE OF CREATIVE VARIATION ===
If the user provides only: Ground Beef, White Rice, Cheese.
Do NOT just return one basic "Beef and Rice Bowl".
Think of the different forms these 3 ingredients can take by changing technique and structure.

Examples of distinct variations for just those 3 ingredients:
1. Cheesy Beef Fried Rice (crisp the rice in a pan, mix beef, melt cheese)
2. Cheese-Stuffed Rice Balls (mix rice & cheese, stuff with beef, pan-fry until crispy)
3. Beef & Rice Casserole (layer rice, add beef, cover with cheese, bake until bubbling)
4. Crispy Rice Beef Cake (flatten rice and cheese into a cake, pan-fry for a crispy bottom, top with beef)
5. Cheesy Beef Rice Wrap (press rice flat to form a "bread" layer, add beef & cheese, fold over like a quesadilla)
6. Rice-Stuffed Beef Patties (flatten beef, put rice & cheese inside, seal and cook)

Use this exact level of creative thinking to generate DISTINCT recipes from whatever limited ingredients the user provides.
=====================================

PRIORITIZATION LOGIC:
Prioritize recipes that:
1. Feel realistic and complete
2. Taste good with limited ingredients
3. Maximize ingredient usage
4. Create interesting texture contrasts
5. Feel visually appealing
6. Are structurally unique from one another

RECIPE COUNT RULE:
- Generate the MAXIMUM number of HIGH-QUALITY distinct recipes possible up to 6.
- Do NOT generate filler recipes just to reach 6.
- If only 2 truly strong recipes are possible, return only 2.
- Return an empty recipes array ONLY if no realistic edible preparation can be made from the ingredients.

QUALITY REQUIREMENTS:
- Be extremely precise and detailed.
- Each step should feel professional and practical.
- Include exhaustive equipment lists, even for basic tools.
- Steps should be logically ordered.
- Cook times and prep times must be realistic.
- kcal must ALWAYS be estimated and NEVER null.
- All required fields MUST be present and non-empty.

IMAGE PROMPT REQUIREMENT:
Each recipe MUST include an "imagePrompt" field describing a highly appetizing food image for generation or retrieval.

The image prompt should:
- describe the final plated dish
- mention texture
- mention lighting/style
- mention serving presentation
- feel visually premium and realistic

Example:
"A crispy cheesy beef rice skillet served in a black cast iron pan with golden melted cheese and crispy rice edges, cinematic lighting, realistic food photography"

DO NOT:
- invent impossible recipes
- invent hidden binding agents
- reference unprovided garnishes
- generate decorative ingredients not provided
- create unrealistic cooking methods
- create duplicate recipe concepts
- create recipes differing only by name

You MUST return ONLY valid JSON in EXACTLY this format:
{
  "recipes": [
    {
      "name": "<recipe title>",
      "cookTime": <integer minutes>,
      "prepTime": <integer minutes>,
      "kcal": <integer>,
      "servings": <integer>,
      "cuisine": "<origin>",
      "category": "<e.g. Main Dish>",
      "tips": "<chef tip>",
      "imagePrompt": "<food photography image prompt>",
      "ingredients": [
        {
          "name": "<ingredient>",
          "quantity": "<quantity with unit>",
          "icon": "<emoji>"
        }
      ],
      "equipment": [
        "<tool 1>",
        "<tool 2>"
      ],
      "steps": [
        "Step 1: ...",
        "Step 2: ..."
      ]
    }
  ]
}

Return ONLY the JSON object.
Do NOT include markdown.
Do NOT include explanations.
Do NOT include prose outside the JSON.${system_instructions ? `\n\n=== ADDITIONAL INSTRUCTIONS FROM SYSTEM ===\n${system_instructions}` : ''}`;
}

/**
 * Schema-validation retry prompt — appended on first schema failure.
 * Makes the schema requirements even more explicit for the retry attempt.
 */
const SCHEMA_RETRY_SUFFIX = `
IMPORTANT CORRECTION: Your previous response did not match the required JSON schema.
You MUST correct this and return ONLY valid JSON with:
- "recipes" array containing between 6 and 10 items (minimum 6, maximum 10)
- Each recipe must have: name (string), ingredients (array of objects), equipment (array of strings), steps (array of strings), cookTime (integer), prepTime (integer), kcal (integer), servings (integer), cuisine (string), category (string)
Return ONLY the corrected JSON object.`;

/**
 * Sends an ingredient list to the configured OpenAI generation model and returns validated recipe data.
 * Implements schema-validation retry logic per spec section 9.
 *
 * Retry policy:
 *  1. First call with normal prompt
 *  2. On schema failure → retry once with amended prompt reinforcing schema
 *  3. On second schema failure → throw 422 SCHEMA_VALIDATION_FAILED
 *  4. On model error → retry once with identical parameters → 500 AI_SERVICE_ERROR
 *
 * @param {string[]} ingredients - Sanitised ingredient list
 * @param {object} [userPreferences] - { allergies: string[], preferences: string[] }
 * @returns {Promise<object[]>} Array of validated recipe objects
 * @throws {AppError} 422 SCHEMA_VALIDATION_FAILED | 500 AI_SERVICE_ERROR
 */
async function generateRecipesFromIngredients(ingredients, userPreferences = {}) {
  const { allergies = [], preferences = [], dislikes = [], DNA = {}, skill_level = {}, cuisines_love = [], kitchen_tools = [], cooking_goals = [], time_minutes = null, servings = null, system_instructions = null } = userPreferences;
  const ingredientList = ingredients.join(', ');

  let schemaFailures = 0;
  let modelFailures = 0;
  let lastError;

  // Max 3 calls: 1 initial + max 1 model retry + max 1 schema retry
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const systemPrompt =
        schemaFailures > 0
          ? buildRecipeSystemPrompt(allergies, preferences, dislikes, DNA, skill_level, cuisines_love, kitchen_tools, cooking_goals, time_minutes, servings, system_instructions) + SCHEMA_RETRY_SUFFIX
          : buildRecipeSystemPrompt(allergies, preferences, dislikes, DNA, skill_level, cuisines_love, kitchen_tools, cooking_goals, time_minutes, servings, system_instructions);

      const response = await timedAiCall(
        'ai.recipe.generate',
        config.openai.generationModel,
        'Recipe generation call',
        () =>
          openaiClient.chat.completions.create({
            model: config.openai.generationModel,
            response_format: { type: 'json_object' },
            messages: [
              { role: 'system', content: systemPrompt },
              {
                role: 'user',
                content: ingredients.length > 0 
                  ? `Generate between 1 and 6 distinct and creative recipes I can cook using ONLY these available ingredients — do NOT add any other ingredient: ${ingredientList}. If these ingredients cannot make at least one realistic recipe on their own, return an empty recipes array.`
                  : `Generate between 1 and 6 popular, mouth-watering, and seasonal recipe suggestions (Main, Healthy, Quick, Chef's Special) that perfectly match my taste DNA, cuisine preferences, and cooking goals. Ensure variety in techniques and flavors.`,
              },
            ],
            max_completion_tokens: 4096,
          }),
        { attempt, ingredient_count: ingredients.length }
      );

      const raw = response.choices[0]?.message?.content;
      if (!raw) throw new Error('Empty response from generation model');

      let parsed;
      try {
        parsed = JSON.parse(raw);
      } catch {
        throw new Error('Model returned invalid JSON');
      }

      // Validate and normalise all recipe fields
      const validated = validateRecipesSchema(parsed);
      logger.event('info', 'ai.recipe.generate.parsed', 'Recipe generation parsed', {
        attempt,
        recipe_count: validated.length,
      });
      return validated;
    } catch (err) {
      lastError = err;

      if (err instanceof AppError && err.code === 'SCHEMA_VALIDATION_FAILED') {
        schemaFailures++;
        if (schemaFailures <= 1) {
          logger.warn(`Recipe schema validation failed (attempt ${attempt}); retrying with amended prompt`);
          continue;
        }
        // Exceeded schema retries
        throw err;
      }

      // Model/network error
      modelFailures++;
      logger.warn(`Recipe generation model failed (attempt ${attempt}): ${err.message}`);
      if (modelFailures <= 1) {
        continue; // Retry once with identical parameters
      }

      // Exceeded model retries
      break;
    }
  }

  if (lastError instanceof AppError) throw lastError;
  throw new AppError(500, 'AI_SERVICE_ERROR', `Generation model failed: ${lastError?.message}`);
}

/**
 * Validates and normalises the raw AI recipe response against the MongoDB schema.
 * Throws 422 SCHEMA_VALIDATION_FAILED if required fields are missing.
 * Allows null nutrition sub-fields per spec ("non-blocking").
 *
 * @param {object} parsed - Parsed JSON from the model
 * @returns {object[]} Array of normalised recipe objects
 * @throws {AppError} 422 SCHEMA_VALIDATION_FAILED
 */
function validateRecipesSchema(parsed) {
  if (!parsed || !Array.isArray(parsed.recipes)) {
    throw new AppError(
      422,
      'SCHEMA_VALIDATION_FAILED',
      'AI response missing "recipes" array'
    );
  }

  // Empty array is valid — means the AI judged the ingredients insufficient
  if (parsed.recipes.length === 0) {
    return [];
  }

  // Allow 0 recipes (means ingredients were insufficient — caller handles this case)
  if (parsed.recipes.length > 6) {
    // Truncate silently to enforce the 6-recipe maximum
    parsed.recipes = parsed.recipes.slice(0, 6);
  }

  return parsed.recipes.map((recipe, idx) => {
    const label = `Recipe[${idx}]`;

    // Required string fields
    if (!recipe.name || typeof recipe.name !== 'string' || !recipe.name.trim()) {
      throw new AppError(422, 'SCHEMA_VALIDATION_FAILED', `${label}: missing or empty "name"`);
    }

    // Required ingredients array
    if (!Array.isArray(recipe.ingredients) || recipe.ingredients.length < 1) {
      throw new AppError(422, 'SCHEMA_VALIDATION_FAILED', `${label}: "ingredients" must be a non-empty array`);
    }

    // Required steps array
    if (!Array.isArray(recipe.steps) || recipe.steps.length < 1) {
      throw new AppError(422, 'SCHEMA_VALIDATION_FAILED', `${label}: "steps" must be a non-empty array`);
    }

    // Required equipment array
    if (!Array.isArray(recipe.equipment)) {
      throw new AppError(422, 'SCHEMA_VALIDATION_FAILED', `${label}: "equipment" must be an array`);
    }

    const name = recipe.name.trim();
    const ingredients = recipe.ingredients.map((ing) => ({
      name: String(ing.name || '').trim(),
      quantity: String(ing.quantity || '').trim(),
      icon: String(ing.icon || '').trim(),
    }));
    const steps = recipe.steps.map(String).map((s) => s.trim());

    // Legacy compatibility (optional)
    const ingredients_to_use = ingredients.map((ing) => `${ing.quantity} ${ing.name}`.trim());

    return {
      name,
      title: name, // Legacy
      recipe_name: name, // Legacy
      ingredients,
      ingredients_to_use, // Legacy
      steps,
      equipment: (recipe.equipment || []).map(String).map((s) => s.trim()),
      additional_ingredients_optional: (recipe.additional_ingredients_optional || []).map(String).map((s) => s.trim()),
      cookTime: Number(recipe.cookTime) || 0,
      prepTime: Number(recipe.prepTime) || 0,
      kcal: Number(recipe.kcal) || 0,
      servings: Number(recipe.servings) || 1,
      cuisine: String(recipe.cuisine || '').trim(),
      category: String(recipe.category || '').trim(),
      tips: String(recipe.tips || '').trim(),
      image: null,
      sourceUrl: '',
    };
  });
}

// ---------------------------------------------------------------------------
// RECIPE IMAGE GENERATION — configured OpenAI image model
// ---------------------------------------------------------------------------

/**
 * Builds a professional food photography prompt for the configured OpenAI image model.
 *
 * @param {string} recipeTitle - Normalised recipe title (e.g. "Chicken Alfredo")
 * @returns {string}
 */
function buildImagePrompt(recipeTitle) {
  return (
    `Professional food photography of ${recipeTitle}, ` +
    'plated beautifully on a rustic ceramic plate, top-down overhead angle, ' +
    'soft natural window lighting, shallow depth of field, appetising, ' +
    'high resolution, vibrant colours, no text, no watermarks, no people'
  );
}

/**
 * Generates a food photograph for a recipe using the configured OpenAI image model.
 *
 * Design decisions:
 *  - Uses response_format:'b64_json' so we get raw bytes to upload to Cloudinary.
 *    Temporary OpenAI image links may expire — Cloudinary gives permanent URLs.
 *  - Non-blocking: returns null on ANY failure so a model outage never breaks
 *    the recipe pipeline.
 *  - No retry: image generation is best-effort; retrying doubles cost with no
 *    guaranteed improvement.
 *  - Runs asynchronously after the HTTP response is already sent (see recipeService).
 *
 * @param {string} recipeTitle - Recipe title used to build the image prompt
 * @returns {Promise<string|null>} Permanent Cloudinary HTTPS URL, or null on failure
 */
async function generateRecipeImage(recipeTitle) {
  if (!recipeTitle || typeof recipeTitle !== 'string' || !recipeTitle.trim()) {
    logger.warn('generateRecipeImage: called with empty or invalid recipeTitle — skipping');
    return null;
  }

  const title = recipeTitle.trim();

  try {
    const response = await timedAiCall(
      'ai.image.generate',
      config.openai.imageModel,
      'Image generation call',
      () =>
        openaiClient.images.generate({
          model: config.openai.imageModel,
          prompt: buildImagePrompt(title),
          n: 1,
          size: '1024x1024',
          quality: 'standard',
          response_format: 'b64_json',
        }),
      { recipe_title: title }
    );

    const b64 = response.data?.[0]?.b64_json;
    if (!b64 || typeof b64 !== 'string') {
      logger.warn(`generateRecipeImage: no b64_json in response for "${title}"`);
      return null;
    }

    // Upload to Cloudinary for a permanent URL (temporary OpenAI links may expire)
    const imageBuffer = Buffer.from(b64, 'base64');
    const cloudinaryUrl = await uploadImage(imageBuffer, `recipe_${Date.now()}.png`);

    logger.event('info', 'ai.image.generate.uploaded', 'Generated image uploaded', {
      recipe_title: title,
      image_url: cloudinaryUrl,
    });
    return cloudinaryUrl;
  } catch (err) {
    logger.event('warn', 'ai.image.generate.fail', 'Image generation failed', {
      recipe_title: title,
      error: err.message,
    });
    return null;
  }
}

/**
 * Generates a list of trending dish names using the configured OpenAI model.
 * 
 * @returns {Promise<string[]>} List of trending dish names
 */
async function generateTrendingDishes() {
  const prompt = `Generate 10 popular and trending dish names from around the world.
Return ONLY raw JSON in this format: {"trending": ["Dish 1", "Dish 2", ...]}
Do NOT include any prose or markdown.`;

  try {
    const response = await timedAiCall(
      'ai.trending.generate',
      config.openai.generationModel,
      'Trending dishes call',
      () =>
        openaiClient.chat.completions.create({
          model: config.openai.generationModel,
          response_format: { type: 'json_object' },
          messages: [{ role: 'user', content: prompt }],
        })
    );

    const raw = response.choices[0]?.message?.content;
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed.trending) ? parsed.trending : [];
  } catch (err) {
    logger.error(`generateTrendingDishes failed: ${err.message}`);
    return ["Sushi", "Tacos", "Pizza", "Pad Thai", "Croissant", "Butter Chicken"]; // Fallback
  }
}

module.exports = {
  detectIngredientsFromImage,
  filterPreDetectedIngredients,
  generateRecipesFromIngredients,
  generateRecipeImage,
  generateTrendingDishes,
  validateRecipesSchema,
  validateIngredientSchema,
};
