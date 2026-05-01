'use strict';

const config = require('./index');

/**
 * OpenAPI 3.0.3 specification for the AI Powered Chef API.
 *
 * All schemas, response shapes, and constraints are derived directly from the
 * live implementation — validators, controllers, and the AI service — so the
 * docs stay accurate as the codebase evolves.
 *
 * Served at:
 *   GET /api/docs        → Swagger UI (interactive browser)
 *   GET /api/docs.json   → Raw OpenAPI JSON (for code-generation tools)
 */
const swaggerSpec = {
  openapi: '3.0.3',

  info: {
    title: 'AI Powered Chef API',
    version: '1.0.0',
    description: `The API uses OpenAI's latest models for ingredient detection
and recipe generation, and DALL·E 3 for recipe photography.

### Stateless Architecture
This API is stateless. It does not use a database or cache. All data is processed
on-the-fly and returned to the caller. Persistence and user profile management
should be handled by the client or a separate backend service.

### Rate Limiting
All AI endpoints share a stricter per-IP rate limit to protect against abuse.
Standard endpoints use a generous global limiter.`,
    contact: {
      name: 'Markhor',
    },
    license: {
      name: 'ISC',
    },
  },

  // Always target the same origin where the docs are served
  // (works locally and behind proxies like Coolify).
  servers: [
    {
      url: '/',
      description: 'Current origin',
    },
  ],

  tags: [
    {
      name: 'Health',
      description: 'Server status and uptime monitoring.',
    },

    {
      name: 'Recipes',
      description:
        'Generate between 6 and 10 personalised recipes from a list of ingredients using the configured OpenAI generation model.',
    },
    {
      name: 'Analyze',
      description:
        'Combined endpoint: upload one photo of your fridge, pantry, or ingredients ' +
        'and receive detected ingredients + 6-10 personalised recipes in a single response. ' +
        'Pass a `user_id` to automatically apply the user\'s saved dietary preferences.',
    },
    {
      name: 'Extraction',
      description:
        'Extract recipe data from social media URLs (YouTube, TikTok, Instagram) or general web pages.',
    },
    {
      name: 'Search',
      description: 'Web search services for recipes.',
    },
  ],

  // ─────────────────────────────────────────────────────────────────────────────
  // PATHS
  // ─────────────────────────────────────────────────────────────────────────────
  paths: {
    // ── POST /api/extract ──────────────────────────────────────────────────────
    '/api/extract': {
      post: {
        tags: ['Extraction'],
        summary: 'Extract recipe from URL',
        description:
          'Extracts a recipe from a social media link (TikTok, Instagram, YouTube) or a general website URL using AI. ' +
          'The process identifies the platform, scrapes the content, and uses OpenAI to structure the recipe data.',
        operationId: 'extractRecipe',
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['url'],
                properties: {
                  url: {
                    type: 'string',
                    format: 'uri',
                    description: 'The URL to extract the recipe from.',
                    example: 'https://www.tiktok.com/@somechef/video/123456789',
                  },
                },
              },
            },
          },
        },
        responses: {
          200: {
            description: 'Recipe extracted and structured successfully.',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    success: { type: 'boolean', example: true },
                    data: {
                      type: 'object',
                      properties: {
                        source: {
                          type: 'string',
                          enum: ['tiktok', 'instagram', 'youtube', 'web'],
                          example: 'tiktok',
                        },
                        image: {
                          type: 'string',
                          format: 'uri',
                          nullable: true,
                          example: 'https://p16-sign.tiktokcdn-us.com/...',
                        },
                        recipe: {
                          type: 'object',
                          properties: {
                            status: { type: 'string', example: 'success' },
                            chef_persona: { type: 'string', example: 'Professional Italian Chef' },
                            recipe: { $ref: '#/components/schemas/Recipe' },
                            fallback_message: { type: 'string', example: '' },
                          },
                        },
                      },
                    },
                    message: { type: 'string', example: 'Recipe extracted successfully' },
                  },
                },
              },
            },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          429: { $ref: '#/components/responses/RateLimitExceeded' },
          500: { $ref: '#/components/responses/InternalError' },
          502: {
            description: 'Bad Gateway — Upstream service (Social Media or OpenAI) failed.',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/ErrorEnvelope' },
              },
            },
          },
        },
      },
    },

    // ── GET /health ────────────────────────────────────────────────────────────
    '/health': {
      get: {
        tags: ['Health'],
        summary: 'Health check',
        description:
          'Returns server status, runtime environment, and current UTC timestamp. ' +
          'Used by load balancers, uptime monitors, and deploy pipelines.',
        operationId: 'getHealth',
        responses: {
          200: {
            description: 'Server is healthy and accepting requests.',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['status', 'env', 'timestamp'],
                  properties: {
                    status: {
                      type: 'string',
                      example: 'ok',
                    },
                    env: {
                      type: 'string',
                      enum: ['development', 'production', 'test'],
                      example: 'development',
                    },
                    timestamp: {
                      type: 'string',
                      format: 'date-time',
                      example: '2026-03-09T12:00:00.000Z',
                    },
                  },
                },
              },
            },
          },
          429: { $ref: '#/components/responses/RateLimitExceeded' },
        },
      },
    },



    // ── POST /api/images/generate ────────────────────────────────────────────
    '/api/images/generate': {
      post: {
        tags: ['Images'],
        summary: 'Generate dish image',
        description: 'Generates a high-quality AI image for a dish name. Returns a permanent Cloudinary URL.',
        operationId: 'generateImage',
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                required: ['name'],
                properties: {
                  name: {
                    type: 'string',
                    description: 'Dish name (e.g. "Chocolate Lava Cake").',
                    example: 'Chocolate Lava Cake',
                  },
                },
              },
            },
          },
        },
        responses: {
          200: {
            description: 'Image generated successfully.',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    success: { type: 'boolean', example: true },
                    image_url: { type: 'string', format: 'uri' },
                  },
                },
              },
            },
          },
          429: { $ref: '#/components/responses/RateLimitExceeded' },
          500: { $ref: '#/components/responses/InternalError' },
        },
      },
    },

    // ── GET /api/search ──────────────────────────────────────────────────────
    '/api/search': {
      get: {
        tags: ['Search'],
        summary: 'Search web for recipes',
        description: 'Searches the web for recipe candidates matching the query.',
        operationId: 'searchWeb',
        parameters: [
          {
            name: 'q',
            in: 'query',
            required: true,
            schema: { type: 'string' },
            description: 'Search query (e.g. "Chicken Tikka Masala").',
          },
        ],
        responses: {
          200: {
            description: 'Search results returned.',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    success: { type: 'boolean', example: true },
                    results: {
                      type: 'array',
                      items: {
                        type: 'object',
                        properties: {
                          title: { type: 'string' },
                          url: { type: 'string', format: 'uri' },
                          snippet: { type: 'string' },
                        },
                      },
                    },
                  },
                },
              },
            },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          500: { $ref: '#/components/responses/InternalError' },
        },
      },
    },

    // ── POST /api/analyze ─────────────────────────────────────────────────────
    '/api/analyze': {
      post: {
        tags: ['Analyze'],
        summary: 'Scan image → detect ingredients → generate 6-10 recipes',
        description: `**Primary mobile app endpoint.** Upload one photo of a fridge, pantry, or ingredient collection and receive detected ingredients plus exactly 5 personalised recipe suggestions in a single API call.

**Pipeline**
1. Image uploaded to Cloudinary and analysed by the configured OpenAI vision model → full ingredient list
2. If \`user_id\` is provided, the user's saved dietary preferences are fetched from MongoDB automatically
3. The configured OpenAI generation model generates between 6 and 10 recipes maximising the detected ingredients and honouring all allergies/preferences
4. Single JSON response with ingredients + recipes

**Caching**
- Caching is currently disabled (Stateless mode).

**Limits**
- Image: max **10 MB**, formats: JPEG, PNG, WebP`,
        operationId: 'analyzeImage',
        requestBody: {
          required: true,
          content: {
            'multipart/form-data': {
              schema: {
                type: 'object',
                required: ['image'],
                properties: {
                  image: {
                    type: 'string',
                    format: 'binary',
                    description: 'Photo of fridge, pantry, or ingredients. JPEG, PNG, or WebP, max 10 MB.',
                  },
                  user_id: {
                    type: 'string',
                    description: 'User identifier. When provided, saved dietary preferences are fetched from the database automatically.',
                    example: 'user_abc123',
                  },
                  user_preferences: {
                    type: 'string',
                    description: 'JSON-stringified UserPreferences. Used only when user_id is not provided or has no saved profile.',
                    example: '{"allergies":["dairy"],"preferences":["vegetarian"],"cuisines_love":["italian"],"kitchen_tools":["microwave","air-fryer"],"cooking_goals":["cook-more-at-home-save-money"],"time_minutes":"1-2 hours"}',
                  },
                },
              },
            },
            'application/json': {
              schema: {
                type: 'object',
                required: ['image_base64'],
                properties: {
                  image_base64: {
                    type: 'string',
                    description: 'Full Base64 data URI (data:image/jpeg|png|webp;base64,...). Decoded size must not exceed 10 MB.',
                    example: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/...',
                  },
                  user_id: {
                    type: 'string',
                    description: 'User identifier for automatic preference lookup.',
                    example: 'user_abc123',
                  },
                  user_preferences: {
                    $ref: '#/components/schemas/UserPreferences',
                  },
                },
              },
            },
          },
        },
        responses: {
          200: {
            description: 'Image analysed and 6-10 personalised recipes generated successfully.',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/AnalyzeResponse' },
                example: {
                  success: true,
                  user_id: 'user_abc123',
                  allowed_ingredients: [
                    { name: 'chicken breast', confidence: 0.96 },
                    { name: 'broccoli', confidence: 0.93 },
                    { name: 'garlic', confidence: 0.91 },
                  ],
                  restricted_ingredients: [
                    { ingredient: 'butter', reason: 'Allergic to dairy' },
                  ],
                  image_url: 'https://res.cloudinary.com/example/image/upload/v1/ai-recipe-app/ingredients/abc123.jpg',
                  recipes: [{ title: 'Garlic Chicken & Broccoli Stir-fry', cook_time: '20 minutes' }],
                },
              },
            },
          },
          400: { $ref: '#/components/responses/BadRequest' },
          422: {
            description: 'No food ingredients detected in the image.',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/ErrorEnvelope' },
                example: {
                  success: false,
                  error: {
                    code: 'NO_INGREDIENTS_FOUND',
                    message: 'No usable ingredients were detected in the image.',
                  },
                },
              },
            },
          },
          429: { $ref: '#/components/responses/RateLimitExceeded' },
          500: { $ref: '#/components/responses/InternalError' },
        },
      },
    },

    // ── GET /api/users ────────────────────────────────────────────────────────
    '/api/users': {
      get: {
        tags: ['Extraction'],
        summary: 'YouTube Info Test',
        description: 'A test endpoint that fetches basic info from a hardcoded YouTube video.',
        operationId: 'getMe',
        responses: {
          200: {
            description: 'Success',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    success: { type: 'boolean', example: true },
                    data: { type: 'object' },
                    message: { type: 'string' },
                  },
                },
              },
            },
          },
        },
      },
    },

    // ── GET /api/recipes/trending ────────────────────────────────────────────
    '/api/recipes/trending': {
      get: {
        tags: ['Recipes'],
        summary: 'Get trending dishes',
        description: 'Returns 10 trending dish names generated by AI.',
        operationId: 'getTrendingDishes',
        responses: {
          200: {
            description: 'List of trending dishes.',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    success: { type: 'boolean', example: true },
                    trending: {
                      type: 'array',
                      items: { type: 'string' },
                      example: ['Sushi', 'Tacos', 'Pizza', 'Pad Thai', 'Croissant', 'Butter Chicken'],
                    },
                  },
                },
              },
            },
          },
          500: { $ref: '#/components/responses/InternalError' },
        },
      },
    },

    // ── POST /api/recipes/suggest ────────────────────────────────────────────
    '/api/recipes/suggest': {
      post: {
        tags: ['Recipes'],
        summary: 'Generate initial recipe suggestions',
        description: 'Generates 10 recipes based on user preferences. No ingredients required.',
        operationId: 'suggestRecipes',
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: {
                  user_preferences: { $ref: '#/components/schemas/UserPreferences' },
                },
              },
            },
          },
        },
        responses: {
          200: {
            description: 'Recipes successfully generated.',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/GenerateRecipesResponse' },
              },
            },
          },
          429: { $ref: '#/components/responses/RateLimitExceeded' },
          500: { $ref: '#/components/responses/InternalError' },
        },
      },
    },

    // ── POST /api/recipes/generate ────────────────────────────────────────────
    '/api/recipes/generate': {
      post: {
        tags: ['Recipes'],
        summary: 'Generate AI recipes',
        description: `Generates **6-10 personalised recipes** from a list of ingredients using the configured OpenAI generation model.

**Caching**
Results are cached in Redis for **1 hour**. The cache key is a SHA-256 hash of
the sorted, lowercase ingredient list combined with the serialised
\`user_preferences\`. Subsequent identical requests return in **< 150 ms**.

**Persistence**
On a cache miss, generated recipes are persisted to MongoDB before being
returned. Cache hits do not create duplicate documents.

**Input sanitisation**
Each ingredient string is sanitised before the AI call:
characters outside \`[a-zA-Z0-9 '-]\` are stripped, whitespace is collapsed,
and the result is truncated to 100 characters. Strings that reduce to empty
are dropped. If **all** strings reduce to empty, a \`400\` is returned.

**Limits**
- ingredients: **1–30 items**, each ≤ 100 characters before sanitisation
- user_preferences strings: each ≤ 50 characters
- Response contains between **6 and 10 recipes**` ,
        operationId: 'generateRecipes',
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { $ref: '#/components/schemas/GenerateRecipesRequest' },
              examples: {
                basic: {
                  summary: 'Basic — 4 common ingredients',
                  value: {
                    ingredients: ['tomato', 'garlic', 'onion', 'olive oil'],
                  },
                },
                withDairyAllergy: {
                  summary: 'With dairy allergy',
                  value: {
                    ingredients: [
                      'chicken',
                      'broccoli',
                      'rice',
                      'garlic',
                      'soy sauce',
                    ],
                    user_preferences: {
                      allergies: ['dairy'],
                      preferences: [],
                    },
                  },
                },
                withPreferences: {
                  summary: 'Vegetarian + low-carb preference',
                  value: {
                    ingredients: [
                      'eggplant',
                      'tomato',
                      'zucchini',
                      'feta cheese',
                      'olive oil',
                    ],
                    user_preferences: {
                      allergies: [],
                      preferences: ['vegetarian', 'low-carb'],
                    },
                  },
                },
                maxIngredients: {
                  summary: 'Maximum 30 ingredients',
                  value: {
                    ingredients: [
                      'chicken breast',
                      'broccoli',
                      'rice',
                      'garlic',
                      'soy sauce',
                      'ginger',
                      'sesame oil',
                      'onion',
                      'bell pepper',
                      'carrot',
                      'celery',
                      'tomato',
                      'mushroom',
                      'spinach',
                      'lemon',
                      'olive oil',
                      'salt',
                      'black pepper',
                      'cumin',
                      'paprika',
                      'turmeric',
                      'coriander',
                      'parsley',
                      'thyme',
                      'bay leaf',
                      'butter',
                      'flour',
                      'egg',
                      'milk',
                      'cheese',
                    ],
                  },
                },
              },
            },
          },
        },
        responses: {
          200: {
            description:
              'Recipes generated (or served from cache) successfully. ' +
              'Contains between 6 and 10 recipe objects.',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/GenerateRecipesResponse' },
                example: {
                  success: true,
                  recipes: [
                    {
                      title: 'Garlic Tomato Pasta',
                      recipe_name: 'Garlic Tomato Pasta',
                      ingredients: [
                        { name: 'tomato', quantity: '3', unit: 'pieces' },
                        { name: 'garlic', quantity: '4', unit: 'cloves' },
                        { name: 'olive oil', quantity: '2', unit: 'tbsp' },
                        { name: 'onion', quantity: '1', unit: 'medium' },
                      ],
                      ingredients_to_use: [
                        '3 pieces tomato',
                        '4 cloves garlic',
                        '2 tbsp olive oil',
                        '1 medium onion',
                      ],
                      additional_ingredients_optional: [
                        'fresh basil',
                        'parmesan cheese',
                      ],
                      instructions: [
                        {
                          step: 1,
                          description: 'Dice the tomatoes and mince the garlic.',
                        },
                        {
                          step: 2,
                          description:
                            'Heat olive oil in a pan over medium heat and sauté the garlic for 2 minutes.',
                        },
                        {
                          step: 3,
                          description:
                            'Add tomatoes and onion, season with salt and pepper, and simmer for 15 minutes.',
                        },
                      ],
                      steps: [
                        'Dice the tomatoes and mince the garlic.',
                        'Heat olive oil in a pan over medium heat and sauté the garlic for 2 minutes.',
                        'Add tomatoes and onion, season with salt and pepper, and simmer for 15 minutes.',
                      ],
                      cook_time: '25 minutes',
                      nutrition: {
                        calories: '420',
                        protein: '12',
                        carbs: '58',
                        fat: '14',
                      },
                    },
                  ],
                },
              },
            },
          },
          400: {
            description:
              'Invalid request body — missing or malformed ingredients, ' +
              'exceeded array limits, or invalid user_preferences.',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/ErrorEnvelope' },
                examples: {
                  missingField: {
                    summary: '"ingredients" field missing',
                    value: {
                      success: false,
                      error: {
                        code: 'INVALID_INGREDIENTS',
                        message: '"ingredients" field is required',
                      },
                    },
                  },
                  emptyArray: {
                    summary: 'Empty ingredients array',
                    value: {
                      success: false,
                      error: {
                        code: 'INVALID_INGREDIENTS',
                        message: 'At least 1 ingredient is required',
                      },
                    },
                  },
                  tooMany: {
                    summary: 'More than 30 ingredients',
                    value: {
                      success: false,
                      error: {
                        code: 'INVALID_INGREDIENTS',
                        message: 'A maximum of 30 ingredients are allowed',
                      },
                    },
                  },
                  notAnArray: {
                    summary: 'ingredients is not an array',
                    value: {
                      success: false,
                      error: {
                        code: 'INVALID_INGREDIENTS',
                        message: '"ingredients" must be an array of strings',
                      },
                    },
                  },
                  allSanitisedAway: {
                    summary: 'All strings reduce to empty after sanitisation',
                    value: {
                      success: false,
                      error: {
                        code: 'INVALID_INGREDIENTS',
                        message:
                          'All ingredients were empty after sanitisation. ' +
                          'Please provide valid ingredient names.',
                      },
                    },
                  },
                  allergyTooLong: {
                    summary: 'Allergy string exceeds 50 characters',
                    value: {
                      success: false,
                      error: {
                        code: 'INVALID_INGREDIENTS',
                        message:
                          '"preferences[0]" length must be less than or equal to 50 characters long',
                      },
                    },
                  },
                },
              },
            },
          },
          422: {
            description:
              'The AI model returned a response that failed schema validation ' +
              'even after one automatic retry with a corrected prompt.',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/ErrorEnvelope' },
                example: {
                  success: false,
                  error: {
                    code: 'SCHEMA_VALIDATION_FAILED',
                    message:
                      'AI response missing "recipes" array or returned 0 recipes',
                  },
                },
              },
            },
          },
          429: { $ref: '#/components/responses/RateLimitExceeded' },
          500: { $ref: '#/components/responses/InternalError' },
        },
      },
    },
  },

  // ─────────────────────────────────────────────────────────────────────────────
  // COMPONENTS
  // ─────────────────────────────────────────────────────────────────────────────
  components: {
    schemas: {
      // ── Shared input ────────────────────────────────────────────────────────
      UserPreferences: {
        type: 'object',
        description:
          'Optional dietary constraints and taste DNA profile. Defaults are applied when omitted.',
        properties: {
          allergies: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            default: [],
            description:
              'Ingredients the user is allergic to. ' +
              'These are excluded from generated recipes and flagged in detection results.',
            example: ['dairy', 'nuts'],
          },
          preferences: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            default: [],
            description:
              'Dietary preferences that guide recipe generation (e.g. "vegetarian", "low-carb").',
            example: ['vegetarian', 'gluten-free'],
          },
          dislikes: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            default: [],
            description:
              'Ingredients the user dislikes. The AI avoids these as primary ingredients where possible.',
            example: ['mushrooms', 'olives'],
          },
          cuisines_love: {
            description:
              'Cuisine names the user loves. Send an array or comma-separated string. The API normalizes values (e.g., "Pakstani" -> "pakistani") and keeps unknown cuisines as normalized slugs for future support. The API also accepts `cuisines` as an alias.',
            oneOf: [
              {
                type: 'array',
                items: { type: 'string', maxLength: 50 },
                example: ['pakistani', 'italian', 'middle-eastern'],
              },
              {
                type: 'string',
                example: 'Pakstani, Italian, Levantine',
              },
            ],
            default: [],
          },
          kitchen_tools: {
            description:
              'Kitchen tools available to the user. Send an array or comma-separated string. The API normalizes values (e.g., "MicroWave" -> "microwave", "Streamer" -> "steamer") and keeps unknown tools as normalized slugs for future support. The API also accepts `kitchen_equipment` as an alias.',
            oneOf: [
              {
                type: 'array',
                items: { type: 'string', maxLength: 50 },
                example: ['oven', 'gas-burner', 'microwave', 'air-fryer', 'instant-pot'],
              },
              {
                type: 'string',
                example: 'Oven, GasBurner, MicroWave, AirFryer, Sous Vide',
              },
            ],
            default: [],
          },
          cooking_goals: {
            description:
              'User cooking goals. Send an array or comma-separated string. The API normalizes common phrases (e.g., "learn to cook from scrath" -> "learn-to-cook-from-scratch") and keeps unknown goals as normalized slugs for future support. The API also accepts `goals` as an alias.',
            oneOf: [
              {
                type: 'array',
                items: { type: 'string', maxLength: 80 },
                example: ['cook-more-at-home-save-money', 'reduce-food-waste', 'eat-healthier-track-nutrition'],
              },
              {
                type: 'string',
                example: 'Cook more at home and save money, Meal prep and plan my week',
              },
            ],
            default: [],
          },
          time_minutes: {
            description:
              'Preferred cooking time. Use null or -1 for any time, a number for exact minutes, or an object { min, max } for a range. The API also accepts `time` as an alias. Strings like "30-45 min" and "1-2 hours" are parsed into minutes.',
            oneOf: [
              { type: 'integer', minimum: -1, example: 30 },
              {
                type: 'object',
                required: ['min', 'max'],
                properties: {
                  min: { type: 'integer', minimum: 0, example: 30 },
                  max: { type: 'integer', minimum: 0, example: 45 },
                },
                example: { min: 30, max: 45 },
              },
            ],
            nullable: true,
            example: 30,
          },
          servings: {
            description:
              'Who you are cooking for. Use null or -1 for "it varies", a number for exact servings, an object { min, max } for a range, or strings like "only me", "couple", "3-4", "5-6", "7+". The API also accepts `cooking_for` as an alias.',
            oneOf: [
              { type: 'integer', minimum: -1, example: 2 },
              {
                type: 'object',
                required: ['min', 'max'],
                properties: {
                  min: { type: 'integer', minimum: 1, example: 3 },
                  max: { type: 'integer', minimum: 1, nullable: true, example: 4 },
                },
                example: { min: 3, max: 4 },
              },
            ],
            nullable: true,
            example: { min: 1, max: 1 },
          },
          DNA: {
            type: 'object',
            description:
              'Taste DNA percentages (0-100). Default keys are Spice, Sweet, Crunchy at 50 each. Additional dimensions are allowed (e.g. Umami).',
            additionalProperties: {
              type: 'number',
              minimum: 0,
              maximum: 100,
            },
            example: {
              Spice: 70,
              Sweet: 40,
              Crunchy: 60,
              Umami: 85,
            },
          },
        },
      },

      // ── Detect response parts ───────────────────────────────────────────────
      AllowedIngredient: {
        type: 'object',
        required: ['name', 'confidence'],
        properties: {
          name: {
            type: 'string',
            description: 'Ingredient name, always lowercase.',
            example: 'tomato',
          },
          confidence: {
            type: 'number',
            format: 'float',
            minimum: 0,
            maximum: 1,
            description:
              'Model confidence score (0.0–1.0). ' +
              'Always `1.0` for pre-detected list inputs whose user has no restrictions.',
            example: 0.97,
          },
        },
      },

      RestrictedIngredient: {
        type: 'object',
        required: ['ingredient', 'reason'],
        properties: {
          ingredient: {
            type: 'string',
            description: 'Ingredient name, always lowercase.',
            example: 'butter',
          },
          reason: {
            type: 'string',
            description:
              'Explanation of why this ingredient violates the user\'s restrictions.',
            example: 'Allergic to dairy',
          },
        },
      },

      DetectIngredientsResponse: {
        type: 'object',
        required: ['success', 'allowed_ingredients', 'restricted_ingredients'],
        properties: {
          success: {
            type: 'boolean',
            example: true,
          },
          allowed_ingredients: {
            type: 'array',
            items: { $ref: '#/components/schemas/AllowedIngredient' },
            description: 'Ingredients that are safe for the user.',
          },
          restricted_ingredients: {
            type: 'array',
            items: { $ref: '#/components/schemas/RestrictedIngredient' },
            description:
              'Ingredients that violate the user\'s allergies or preferences.',
          },
          image_url: {
            type: 'string',
            format: 'uri',
            nullable: true,
            description:
              'Cloudinary URL of the uploaded image. ' +
              '`null` when the pre-detected list mode is used (no image is uploaded).',
            example:
              'https://res.cloudinary.com/example/image/upload/v1/ai-recipe-app/ingredients/abc123.jpg',
          },
        },
      },

      // ── Recipe response parts ───────────────────────────────────────────────
      RecipeIngredient: {
        type: 'object',
        required: ['name', 'quantity'],
        description: 'A single ingredient entry within a recipe.',
        properties: {
          name: {
            type: 'string',
            description: 'Ingredient name.',
            example: 'garlic',
          },
          quantity: {
            type: 'string',
            description: 'Quantity with unit.',
            example: '4 cloves',
          },
          icon: {
            type: 'string',
            description: 'Emoji icon.',
            example: '🧄',
          },
        },
      },

      Nutrition: {
        type: 'object',
        description:
          'Estimated nutritional information per serving. ' +
          'All sub-fields are numeric strings with units stripped (e.g. `"420"` not `"420 kcal"`). ' +
          'Any sub-field may be `null` if the model cannot estimate it reliably.',
        properties: {
          calories: {
            type: 'string',
            nullable: true,
            description: 'Estimated calories per serving (numeric string, no unit).',
            example: '420',
          },
          protein: {
            type: 'string',
            nullable: true,
            description: 'Estimated protein per serving in grams (numeric string, no unit).',
            example: '12',
          },
          carbs: {
            type: 'string',
            nullable: true,
            description: 'Estimated carbohydrates per serving in grams (numeric string, no unit).',
            example: '58',
          },
          fat: {
            type: 'string',
            nullable: true,
            description: 'Estimated fat per serving in grams (numeric string, no unit).',
            example: '14',
          },
        },
      },

      Recipe: {
        type: 'object',
        required: [
          'name',
          'ingredients',
          'steps',
          'equipment',
          'cookTime',
          'prepTime',
          'kcal',
          'servings',
        ],
        description: 'A single AI-generated recipe.',
        properties: {
          name: {
            type: 'string',
            description: 'Recipe title.',
            example: 'Garlic Tomato Pasta',
          },
          image: {
            type: 'string',
            format: 'uri',
            nullable: true,
            example: null,
          },
          ingredients: {
            type: 'array',
            items: { $ref: '#/components/schemas/RecipeIngredient' },
            minItems: 1,
            description: 'Structured list of ingredients.',
          },
          steps: {
            type: 'array',
            items: { type: 'string' },
            minItems: 1,
            description: 'Step-by-step instructions.',
            example: [
              'Step 1: Dice the tomatoes and mince the garlic.',
              'Step 2: Sauté garlic in olive oil for 2 minutes.',
            ],
          },
          equipment: {
            type: 'array',
            items: { type: 'string' },
            description: 'List of necessary kitchen tools and equipment.',
            example: ['Stove', 'Large pan', "Chef's knife"],
          },
          cookTime: {
            type: 'integer',
            description: 'Estimated cook time in minutes.',
            example: 25,
          },
          prepTime: {
            type: 'integer',
            description: 'Estimated prep time in minutes.',
            example: 10,
          },
          kcal: {
            type: 'integer',
            description: 'Estimated calories per serving.',
            example: 420,
          },
          servings: {
            type: 'integer',
            description: 'Number of servings.',
            example: 2,
          },
          cuisine: {
            type: 'string',
            description: 'Cuisine origin.',
            example: 'Italian',
          },
          category: {
            type: 'string',
            description: 'Meal category.',
            example: 'Main Dish',
          },
          tips: {
            type: 'string',
            description: 'Chef tips.',
            example: 'Use fresh basil for better aroma.',
          },
          sourceUrl: {
            type: 'string',
            description: 'Original recipe URL if applicable.',
            example: '',
          },
        },
      },

      GenerateRecipesRequest: {
        type: 'object',
        required: ['ingredients'],
        properties: {
          ingredients: {
            type: 'array',
            items: {
              type: 'string',
              maxLength: 100,
            },
            minItems: 1,
            maxItems: 30,
            description:
              'List of available ingredient names. ' +
              'Strings are sanitised before the AI call: characters outside `[a-zA-Z0-9 \'-]` ' +
              'are stripped, whitespace is collapsed, and the result is truncated to 100 characters.',
            example: ['tomato', 'garlic', 'onion', 'olive oil'],
          },
          user_preferences: {
            $ref: '#/components/schemas/UserPreferences',
          },
        },
      },

      GenerateRecipesResponse: {
        type: 'object',
        required: ['success', 'recipes'],
        properties: {
          success: {
            type: 'boolean',
            example: true,
          },
          recipes: {
            type: 'array',
            items: { $ref: '#/components/schemas/Recipe' },
            minItems: 6,
            maxItems: 10,
            description: 'Contains between 6 and 10 recipes.',
          },
        },
      },

      // ── Analyze response ────────────────────────────────────────────────────
      AnalyzeResponse: {
        type: 'object',
        required: ['success', 'allowed_ingredients', 'restricted_ingredients', 'recipes'],
        description: 'Combined response from the /api/analyze endpoint — ingredients + 6-10 recipes.',
        properties: {
          success: { type: 'boolean', example: true },
          user_id: {
            type: 'string',
            nullable: true,
            description: 'The user_id that was provided in the request, or null if anonymous.',
            example: 'user_abc123',
          },
          allowed_ingredients: {
            type: 'array',
            items: { $ref: '#/components/schemas/AllowedIngredient' },
            description: 'Ingredients detected in the image that are safe for the user.',
          },
          restricted_ingredients: {
            type: 'array',
            items: { $ref: '#/components/schemas/RestrictedIngredient' },
            description: 'Ingredients detected in the image that violate the user\'s restrictions.',
          },
          image_url: {
            type: 'string',
            format: 'uri',
            nullable: true,
            description: 'Cloudinary URL of the uploaded image.',
            example: 'https://res.cloudinary.com/example/image/upload/v1/ai-recipe-app/ingredients/abc123.jpg',
          },
          recipes: {
            type: 'array',
            items: { $ref: '#/components/schemas/Recipe' },
            minItems: 6,
            maxItems: 10,
            description: 'Between 6 and 10 personalised recipes.',
          },
        },
      },

      // ── User preferences response ───────────────────────────────────────────
      UserPreferencesResponse: {
        type: 'object',
        required: ['success', 'user_id', 'allergies', 'preferences', 'dislikes', 'cuisines_love', 'kitchen_tools', 'cooking_goals', 'DNA', 'time_minutes', 'servings'],
        properties: {
          success: { type: 'boolean', example: true },
          user_id: { type: 'string', example: 'user_abc123' },
          allergies: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            description: 'Saved allergy list.',
            example: ['dairy', 'nuts'],
          },
          preferences: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            description: 'Saved dietary preferences.',
            example: ['vegetarian', 'low-carb'],
          },
          dislikes: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            description: 'Saved disliked ingredients.',
            example: ['mushrooms', 'olives'],
          },
          cuisines_love: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            description:
              'Saved loved cuisines after normalization. Unknown future cuisines are kept as normalized slugs.',
            example: ['pakistani', 'italian', 'levantine'],
          },
          kitchen_tools: {
            type: 'array',
            items: { type: 'string', maxLength: 50 },
            description:
              'Saved kitchen tools after normalization. Unknown future tools are kept as normalized slugs.',
            example: ['oven', 'gas-burner', 'microwave', 'air-fryer', 'sous-vide'],
          },
          cooking_goals: {
            type: 'array',
            items: { type: 'string', maxLength: 80 },
            description:
              'Saved cooking goals after normalization. Unknown future goals are kept as normalized slugs.',
            example: ['cook-more-at-home-save-money', 'reduce-food-waste', 'learn-to-cook-from-scratch'],
          },
          DNA: {
            type: 'object',
            additionalProperties: {
              type: 'number',
              minimum: 0,
              maximum: 100,
            },
            description:
              'Saved taste DNA profile. Always includes default keys Spice, Sweet, Crunchy.',
            example: {
              Spice: 50,
              Sweet: 50,
              Crunchy: 50,
              Umami: 75,
            },
          },
          time_minutes: {
            description:
              'Saved cooking time preference. Null means any time; otherwise exact minutes or a range object { min, max }. Request bodies may also use `time` as an alias.',
            oneOf: [
              { type: 'integer', minimum: 0, example: 30 },
              {
                type: 'object',
                required: ['min', 'max'],
                properties: {
                  min: { type: 'integer', minimum: 0, example: 30 },
                  max: { type: 'integer', minimum: 0, example: 45 },
                },
                example: { min: 30, max: 45 },
              },
            ],
            nullable: true,
            example: null,
          },
          servings: {
            description:
              'Saved servings preference. Null means "it varies"; otherwise a range object { min, max } where max can be null for open-ended values like 7+.',
            type: 'object',
            nullable: true,
            properties: {
              min: { type: 'integer', minimum: 1, example: 2 },
              max: { type: 'integer', minimum: 1, nullable: true, example: 2 },
            },
            example: { min: 2, max: 2 },
          },
        },
      },

      // ── Error envelope (all error responses) ────────────────────────────────
      ErrorEnvelope: {
        type: 'object',
        required: ['success', 'error'],
        description: 'Standard error response format used by all endpoints.',
        properties: {
          success: {
            type: 'boolean',
            example: false,
          },
          error: {
            type: 'object',
            required: ['code', 'message'],
            properties: {
              code: {
                type: 'string',
                description: 'Machine-readable error code.',
                enum: [
                  'INVALID_IMAGE',
                  'INVALID_INGREDIENTS',
                  'NO_INGREDIENTS_FOUND',
                  'SCHEMA_VALIDATION_FAILED',
                  'RATE_LIMIT_EXCEEDED',
                  'AI_SERVICE_ERROR',
                  'STORAGE_ERROR',
                  'NOT_FOUND',
                  'INTERNAL_SERVER_ERROR',
                ],
                example: 'INVALID_IMAGE',
              },
              message: {
                type: 'string',
                description:
                  'Human-readable description for display or logging. ' +
                  'Internal stack traces are never included.',
                example: 'No image file provided.',
              },
            },
          },
        },
      },
    },

    // ── Shared responses ───────────────────────────────────────────────────────
    responses: {
      BadRequest: {
        description: 'Invalid request — missing or malformed input.',
        content: {
          'application/json': {
            schema: { $ref: '#/components/schemas/ErrorEnvelope' },
            example: {
              success: false,
              error: { code: 'INVALID_IMAGE', message: 'No image provided.' },
            },
          },
        },
      },

      RateLimitExceeded: {
        description:
          'Per-IP rate limit exceeded. ' +
          'AI endpoints: 20 req / 15 min in production, 500 req / 15 min in development. ' +
          'Standard endpoints: 100 req / 15 min in production.',
        headers: {
          'RateLimit-Limit': {
            schema: { type: 'integer' },
            description: 'Maximum allowed requests in the current window.',
          },
          'RateLimit-Remaining': {
            schema: { type: 'integer' },
            description: 'Requests remaining in the current window.',
          },
          'RateLimit-Reset': {
            schema: { type: 'integer' },
            description: 'Unix timestamp (seconds) when the window resets.',
          },
        },
        content: {
          'application/json': {
            schema: { $ref: '#/components/schemas/ErrorEnvelope' },
            example: {
              success: false,
              error: {
                code: 'RATE_LIMIT_EXCEEDED',
                message:
                  'AI endpoint rate limit exceeded. Please wait before making more requests.',
              },
            },
          },
        },
      },

      InternalError: {
        description:
          'Unexpected server error. An external dependency (OpenAI or Cloudinary) ' +
          'may have failed. Internal details are never exposed to the client.',
        content: {
          'application/json': {
            schema: { $ref: '#/components/schemas/ErrorEnvelope' },
            examples: {
              aiServiceError: {
                summary: 'OpenAI API failure',
                value: {
                  success: false,
                  error: {
                    code: 'AI_SERVICE_ERROR',
                    message: 'An unexpected error occurred. Please try again later.',
                  },
                },
              },
              storageError: {
                summary: 'Cloudinary upload failure',
                value: {
                  success: false,
                  error: {
                    code: 'STORAGE_ERROR',
                    message: 'An unexpected error occurred. Please try again later.',
                  },
                },
              },
            },
          },
        },
      },
    },
  },
};

module.exports = { swaggerSpec };
