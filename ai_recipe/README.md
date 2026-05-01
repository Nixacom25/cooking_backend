# AI Powered Chef API

A production-ready REST API for an AI-powered recipe mobile application. Uses OpenAI's latest model family by default for vision/text (**gpt-5** and **gpt-5-mini**), with **DALL·E 3** for recipe images. Backed by MongoDB, Redis caching, and Cloudinary image storage.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Environment Variables](#environment-variables)
- [Getting Started](#getting-started)
- [Architecture Overview](#architecture-overview)
- [Rate Limiting](#rate-limiting)
- [Caching Strategy](#caching-strategy)
- [Image Upload Constraints](#image-upload-constraints)
- [Standard Response Envelopes](#standard-response-envelopes)
- [Error Codes Reference](#error-codes-reference)
- [Shared Schemas](#shared-schemas)
  - [user_preferences Object](#user_preferences-object)
  - [Recipe Object](#recipe-object)
- [API Endpoints](#api-endpoints)
  - [GET /health](#1-get-health)
  - [POST /api/ingredients/detect](#2-post-apiingredientsdetect)
  - [POST /api/recipes/generate](#3-post-apirecipesgenerate)
  - [POST /api/analyze](#4-post-apianalyze)
  - [GET /api/users/:userId/preferences](#5-get-apiusersuseridpreferences)
  - [PUT /api/users/:userId/preferences](#6-put-apiusersuseridpreferences)
  - [DELETE /api/users/:userId/preferences](#7-delete-apiusersuseridpreferences)
- [AI Models & Prompts](#ai-models--prompts)
- [Background Image Generation](#background-image-generation)
- [Database Schemas](#database-schemas)
- [Startup & Graceful Shutdown](#startup--graceful-shutdown)
- [Project Structure](#project-structure)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Node.js v18+ |
| Framework | Express.js 5.2.1 |
| Database | MongoDB 9.2.4 via Mongoose |
| Cache | Redis via ioredis 5.10.0 |
| AI — Vision | OpenAI gpt-5 (default, configurable) |
| AI — Generation | OpenAI gpt-5-mini (default, configurable) |
| AI — Images | OpenAI DALL·E 3 (default, configurable) |
| Image Storage | Cloudinary |
| File Uploads | Multer (memory storage) |
| Validation | Joi |
| Security | Helmet.js, express-rate-limit |
| Logging | Winston |

---

## Environment Variables

### Required

| Variable | Description |
|---|---|
| `OPENAI_API_KEY` | OpenAI API key |
| `MONGODB_URI` | MongoDB connection string (supports `mongodb+srv://`) |
| `MONGODB_DB_NAME` | Target database name |
| `REDIS_URL` | Redis connection URL |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret |

### Optional

| Variable | Default | Description |
|---|---|---|
| `PORT` | `3000` | HTTP server port |
| `NODE_ENV` | `development` | Environment: `development`, `production`, `test` |
| `LOG_LEVEL` | `info` (prod) / `debug` (dev) | Winston log level |
| `CORS_ORIGINS` | `*` | Comma-separated allowed origins |
| `AI_RATE_LIMIT` | `20` (prod) / `500` (dev) | AI endpoint max requests per 15 min |
| `OPENAI_VISION_MODEL` | `gpt-5` | OpenAI model used for ingredient detection |
| `OPENAI_GENERATION_MODEL` | `gpt-5-mini` | OpenAI model used for recipe generation and ingredient filtering |
| `OPENAI_IMAGE_MODEL` | `dall-e-3` | OpenAI image model used for recipe photos |
| `CLOUDINARY_FOLDER` | `ai-recipe-app/ingredients` | Cloudinary upload folder |
| `ENABLE_DOCS` | `false` | Set `true` to expose Swagger UI in production |

---

## Getting Started

```bash
# Install dependencies
npm install

# Copy and fill environment variables
cp .env.example .env

# Start development server
npm run dev

# Start production server
npm start
```

API docs available at `http://localhost:3000/api/docs` (development) or when `ENABLE_DOCS=true`.

---

## Architecture Overview

```
Mobile App
    │
    ▼
Express App (app.js)
    │
    ├── Helmet (security headers)
    ├── CORS (all origins)
    ├── Body parser (JSON 20 MB limit for base64)
    ├── Compression (gzip)
    ├── Morgan (HTTP logging)
    │
    ├── GET  /health
    ├── POST /api/ingredients/detect  ─── aiEndpointLimiter → Multer → Validators → Controller
    ├── POST /api/recipes/generate    ─── aiEndpointLimiter → Validator → Controller
    ├── POST /api/analyze             ─── aiEndpointLimiter → Multer → Validators → Controller
    ├── GET  /api/users/:id/preferences ── apiRateLimiter → Controller
    ├── PUT  /api/users/:id/preferences ── apiRateLimiter → Controller
    └── DELETE /api/users/:id/preferences ─ apiRateLimiter → Controller

Controllers call Services:
    ├── ingredientService  → aiService (OpenAI vision model) + cacheService (Redis) + storageService (Cloudinary)
    ├── recipeService      → aiService (OpenAI generation + image models) + cacheService + MongoDB
    └── userService        → MongoDB User collection

Error flow:  AppError → asyncHandler → global errorHandler middleware
```

### Request Timeout

All HTTP requests time out after **35 seconds** (configured on the HTTP server directly). This allows OpenAI generation (up to 30 s) plus response overhead.

---

## Rate Limiting

Two independent rate limiters are applied per-route, keyed by client IP (via `trust proxy`).

| Limiter | Applied To | Window | Prod Limit | Dev Limit |
|---|---|---|---|---|
| `apiRateLimiter` | `/health`, all `/api/users` routes | 15 min | 100 req | 1000 req |
| `aiEndpointLimiter` | `/api/ingredients/detect`, `/api/recipes/generate`, `/api/analyze` | 15 min | 20 req | 500 req |

**`aiEndpointLimiter`** has `skipFailedRequests: true` — HTTP 400 validation errors do **not** consume quota.

Rate limit headers returned on every response (draft-7 format):

| Header | Description |
|---|---|
| `RateLimit-Limit` | Max requests allowed in window |
| `RateLimit-Remaining` | Requests left in current window |
| `RateLimit-Reset` | Unix timestamp when the window resets |

When the limit is exceeded, the response is:

```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests, please try again later."
  }
}
```

---

## Caching Strategy

All caching is best-effort — Redis failures are silently logged and never propagate errors to clients.

### Cache Keys

| Data | Key Pattern | TTL |
|---|---|---|
| Image ingredient detection | `ingredient:{sha256(imageBuffer + preferences)}` | 24 hours |
| URL ingredient detection | `ingredient:url:{sha256(imageUrl + preferences)}` | 24 hours |
| Pre-detected list filtering | `ingredient:list:{sha256(sortedIngredients + preferences)}` | 24 hours |
| Recipe generation | `recipe:{sha256(sortedIngredients + preferences)}` | 1 hour |

Cache keys are **order-invariant**: ingredients are sorted and lowercased before hashing, so `["garlic","tomato"]` and `["tomato","garlic"]` produce the same key.

### Cache Flow — Ingredients

```
Request
  └── Hash (buffer + prefs) → check Redis
        ├── HIT  → return immediately (< 50 ms)
        └── MISS → upload to Cloudinary → OpenAI vision model → cache result (24h) → return
```

### Cache Flow — Recipes

```
Request
  └── Hash (sorted ingredients + prefs) → check Redis
        ├── HIT  → return immediately (< 150 ms)
        └── MISS → OpenAI generation model → validate → save to MongoDB → cache result (1h) → return
              └── Background: OpenAI image model → Cloudinary → update Redis + MongoDB
```

Recipes are initially returned with `"image_url": null`. A background task generates recipe food photography in parallel, then updates both Redis and MongoDB — the next cache hit or DB read will include the real image URL.

---

## Image Upload Constraints

| Constraint | Value |
|---|---|
| Max file size | 10 MB |
| Accepted MIME types | `image/jpeg`, `image/png`, `image/webp` |
| Accepted extensions | `.jpg`, `.jpeg`, `.png`, `.webp` |
| Storage engine | Multer `memoryStorage` (never written to disk) |
| Magic byte validation | Yes — JPEG (`FF D8 FF`), PNG (`89 50 4E 47`), WebP (`RIFF...WEBP`) |
| Final storage | Cloudinary (CDN, permanent HTTPS URLs) |

---

## Standard Response Envelopes

### Success

```json
{
  "success": true,
  ...endpoint-specific fields
}
```

### Error

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description."
  }
}
```

Stack traces are **never** included in error responses.

---

## Error Codes Reference

| HTTP | Code | When It Occurs |
|---|---|---|
| `400` | `INVALID_IMAGE` | No image provided; unsupported MIME type or extension; file exceeds 10 MB; corrupt or invalid magic bytes; malformed Base64 data URI prefix |
| `400` | `INVALID_INGREDIENTS` | Ingredients array contains non-string items; more than 50 items; all ingredients reduce to empty strings after sanitization; malformed `user_preferences` |
| `400` | `INVALID_USER_ID` | `userId` path parameter is missing or empty after trimming |
| `400` | `INVALID_PREFERENCES` | Joi schema validation failure; invalid `time_minutes` format; invalid `servings` format; `DNA` values out of 0–100 range; invalid `skill_level` |
| `422` | `NO_INGREDIENTS_FOUND` | OpenAI vision model returned no food ingredients from the image |
| `422` | `SCHEMA_VALIDATION_FAILED` | AI recipe response missing required fields; recipe count outside 3–5; validation of parsed JSON failed after 2 retries |
| `429` | `RATE_LIMIT_EXCEEDED` | Request quota exhausted for current IP |
| `500` | `AI_SERVICE_ERROR` | OpenAI API returned an error after 2 retries; JSON parse failure on AI response |
| `500` | `STORAGE_ERROR` | Cloudinary upload failed; MongoDB insert/update failed; user preference save/lookup failed |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected programming error (generic message sent, full details logged internally) |

---

## Shared Schemas

### `user_preferences` Object

Used in `/api/ingredients/detect`, `/api/recipes/generate`, and `/api/analyze`. All fields are optional.

| Field | Type | Constraints | Description |
|---|---|---|---|
| `allergies` | `String[]` | Each ≤ 50 chars | Hard exclusions — allergens never appear in any recipe |
| `preferences` | `String[]` | Each ≤ 50 chars | Dietary preferences (e.g. `"vegetarian"`, `"low-carb"`, `"gluten-free"`) |
| `dislikes` | `String[]` | Each ≤ 50 chars | Soft exclusions — avoided as main ingredients, may appear as optional extras |
| `cuisines_love` | `String[]` or comma-separated `String` | Each item ≤ 50 chars | Preferred cuisine styles (e.g. `"italian"`, `"thai"`, `"pakistani"`) |
| `kitchen_tools` | `String[]` or comma-separated `String` | Each item ≤ 50 chars | Available equipment (e.g. `"oven"`, `"blender"`, `"instant-pot"`, `"air-fryer"`) |
| `cooking_goals` | `String[]` or comma-separated `String` | Each item ≤ 80 chars | User goals (e.g. `"reduce-food-waste"`, `"meal-prep-plan-week"`, `"eat-healthier-track-nutrition"`) |
| `time_minutes` | `Number` or `String` | See below | Target cooking time budget |
| `servings` | `Number` or `String` | See below | Target serving count |
| `DNA` | `Object<String, Number>` | Values `0–100` | Flavor dimension weights. Keys: `"Spice"`, `"Sweet"`, `"Crunchy"`, or any custom dimension |
| `skill_level` | `String` \| `Number` \| `Object` | See below | Cook's skill level |

#### `time_minutes` accepted formats

| Input | Meaning |
|---|---|
| `null` or `"any"` or `-1` | No time constraint |
| `30` | Exactly 30 minutes |
| `"30"` | Exactly 30 minutes |
| `"30-45 min"` | Between 30 and 45 minutes |
| `"under 1 hour"` | ≤ 60 minutes |
| `"1-2 hours"` | 60–120 minutes |
| `{ "min": 20, "max": 45 }` | Between 20 and 45 minutes |

Max accepted value: 1440 (24 hours).

#### `servings` accepted formats

| Input | Meaning |
|---|---|
| `null` or `"it varies"` or `-1` | No serving constraint |
| `4` | Exactly 4 servings |
| `"couple"` | 2 servings |
| `"2-4"` | Between 2 and 4 servings |
| `"7+"` | 7 or more servings (open-ended) |
| `{ "min": 2, "max": 4 }` | Between 2 and 4 servings |
| `{ "min": 7, "max": null }` | 7 or more (open-ended) |

Max accepted value: 50.

#### `skill_level` accepted formats

| Input | Meaning |
|---|---|
| `"total_beginer"` | Beginner (< 20%) — simple methods, minimal techniques |
| `"HomeCook"` | Home cook (20–50%) — moderate complexity |
| `"ConfidentCook"` | Confident cook (50–75%) — advanced techniques |
| `"Advanced Semi Pro"` | Advanced (> 75%) — professional-level |
| `45` (Number 0–100) | Mapped to nearest label automatically |
| `{ "label": "HomeCook", "percent": 45 }` | Explicit object form |

#### `DNA` flavor profile

Default values when not provided: `{ "Spice": 50, "Sweet": 50, "Crunchy": 50 }`.

Higher values bias recipes towards that flavor dimension (e.g. `"Spice": 80` → spicier recipes). Custom keys like `"Umami"`, `"Sour"`, `"Bitter"` are also accepted.

#### Field name aliases

The following alternative field names are automatically mapped to their canonical equivalents:

| Alias | Canonical Field |
|---|---|
| `time` | `time_minutes` |
| `cooking_for` | `servings` |
| `cuisines` | `cuisines_love` |
| `kitchen_equipment` | `kitchen_tools` |
| `goals` | `cooking_goals` |

---

### Recipe Object

Returned inside `recipes[]` arrays on all recipe-generating endpoints.

| Field | Type | Description |
|---|---|---|
| `title` | `String` | Recipe title |
| `recipe_name` | `String` | Same as `title` (AI compatibility field) |
| `ingredients` | `Array` | Structured ingredient list (see below) |
| `ingredients[].name` | `String` | Ingredient name |
| `ingredients[].quantity` | `String` | Amount (e.g. `"2"`, `"300"`) |
| `ingredients[].unit` | `String` | Unit (e.g. `"pieces"`, `"g"`, `"tbsp"`) — may be omitted |
| `ingredients_to_use` | `String[]` | Flat human-readable ingredient strings (e.g. `"2 pieces chicken breast"`) |
| `additional_ingredients_optional` | `String[]` | Optional extras not required to make the dish |
| `instructions` | `Array` | Structured step-by-step instructions |
| `instructions[].step` | `Number` | Step number (1-indexed) |
| `instructions[].description` | `String` | Step text |
| `steps` | `String[]` | Flat list of instruction strings (same content as `instructions`) |
| `cook_time` | `String` | Total estimated cooking time (e.g. `"35 minutes"`) |
| `nutrition` | `Object` | Estimated nutritional info per serving |
| `nutrition.calories` | `String` \| `null` | Calories — always estimated, rarely null |
| `nutrition.protein` | `String` \| `null` | Protein in grams |
| `nutrition.carbs` | `String` \| `null` | Carbohydrates in grams |
| `nutrition.fat` | `String` \| `null` | Fat in grams |
| `image_url` | `String` \| `null` | OpenAI image-model generated food photo on Cloudinary. Initially `null`, populated asynchronously |

---

## API Endpoints

### 1. `GET /health`

Server liveness check. Used by load balancers and uptime monitors.

**Rate Limit:** `apiRateLimiter`  
**Authentication:** None

#### Response `200 OK`

```json
{
  "status": "ok",
  "env": "development",
  "timestamp": "2026-04-02T10:00:00.000Z"
}
```

---

### 2. `POST /api/ingredients/detect`

Detects food ingredients from an image using the configured OpenAI vision model. Categorizes results into `allowed_ingredients` and `restricted_ingredients` based on the user's allergies and dietary preferences.

Supports three mutually exclusive input modes.

**Rate Limit:** `aiEndpointLimiter`  
**Authentication:** None  
**Cache:** Redis 24-hour TTL

#### Middleware chain

```
aiEndpointLimiter
  → upload.single('image')       (parses multipart if Content-Type is multipart/form-data)
  → handleUploadError            (converts Multer errors to AppError)
  → validateUserPreferences      (parses + normalizes user_preferences)
  → validateImageUpload          (validates file/buffer format and size)
  → detectIngredientsHandler
```

---

#### Input Mode A — Multipart File Upload

**Content-Type:** `multipart/form-data`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `image` | `File` | Yes | JPEG / PNG / WebP, max 10 MB | Image file |
| `user_preferences` | `String` | No | JSON-stringified object | Dietary preferences |

```
POST /api/ingredients/detect
Content-Type: multipart/form-data

image:            [binary file]
user_preferences: {
  "allergies": ["dairy"],
  "preferences": ["vegetarian"],
  "dislikes": ["mushrooms"],
  "cuisines_love": ["italian"],
  "kitchen_tools": ["oven"],
  "cooking_goals": ["reduce-food-waste"],
  "time_minutes": 30,
  "servings": "2-4",
  "DNA": { "Spice": 60, "Sweet": 30 },
  "skill_level": "HomeCook"
}
```

---

#### Input Mode B — Base64 Image (JSON)

**Content-Type:** `application/json`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `image_base64` | `String` | Yes | Data URI: `data:image/(jpeg\|png\|webp);base64,...` | Base64-encoded image |
| `user_preferences` | `Object` | No | — | Dietary preferences |

```json
{
  "image_base64": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/...",
  "user_preferences": {
    "allergies": ["dairy", "nuts"],
    "preferences": ["vegetarian"],
    "dislikes": ["mushrooms", "olives"],
    "cuisines_love": ["italian", "mediterranean"],
    "kitchen_tools": ["oven", "blender"],
    "cooking_goals": ["reduce-food-waste", "eat-healthier-track-nutrition"],
    "time_minutes": "30-45 min",
    "servings": "2-4",
    "DNA": { "Spice": 40, "Sweet": 60, "Crunchy": 50 },
    "skill_level": "HomeCook"
  }
}
```

---

#### Input Mode C — Pre-detected Ingredient List (JSON)

When the client already knows the ingredient names (e.g. from a previous scan), this mode skips the vision model and only applies the allergy/preference filter.

**Content-Type:** `application/json`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `ingredients` | `String[]` | Yes | 1–50 items, each a non-empty string | Pre-detected ingredient names |
| `user_preferences` | `Object` | No | — | Dietary preferences |

> If the user has no allergies or preferences, this mode returns the list immediately with `confidence: 1.0` — no AI call is made.

```json
{
  "ingredients": [
    "chicken breast",
    "broccoli",
    "garlic",
    "olive oil",
    "butter",
    "cheddar cheese"
  ],
  "user_preferences": {
    "allergies": ["dairy"],
    "preferences": ["low-carb"],
    "dislikes": ["olives"],
    "cuisines_love": ["asian"],
    "kitchen_tools": ["wok", "oven"],
    "cooking_goals": ["reduce-food-waste"],
    "time_minutes": 30,
    "servings": 2,
    "DNA": { "Spice": 70, "Sweet": 20 },
    "skill_level": "ConfidentCook"
  }
}
```

---

#### Response `200 OK`

```json
{
  "success": true,
  "allowed_ingredients": [
    { "name": "chicken breast", "confidence": 0.96 },
    { "name": "broccoli", "confidence": 0.93 },
    { "name": "garlic", "confidence": 0.91 },
    { "name": "olive oil", "confidence": 0.89 }
  ],
  "restricted_ingredients": [
    { "ingredient": "butter", "reason": "Allergic to dairy" },
    { "ingredient": "cheddar cheese", "reason": "Allergic to dairy" }
  ],
  "image_url": "https://res.cloudinary.com/your-cloud/image/upload/v1234567890/ai-recipe-app/ingredients/abc123.jpg"
}
```

> `image_url` is `null` when using Input Mode C (pre-detected list) since no image is uploaded.

| Field | Type | Description |
|---|---|---|
| `success` | `Boolean` | Always `true` |
| `allowed_ingredients` | `Array` | Ingredients safe to use given the user's restrictions |
| `allowed_ingredients[].name` | `String` | Lowercase ingredient name |
| `allowed_ingredients[].confidence` | `Number` | AI confidence score (`0.0–1.0`). Always `1.0` for pre-detected list with no restrictions |
| `restricted_ingredients` | `Array` | Ingredients excluded due to allergies or dietary preferences |
| `restricted_ingredients[].ingredient` | `String` | Ingredient name |
| `restricted_ingredients[].reason` | `String` | Human-readable reason for restriction |
| `image_url` | `String` \| `null` | Cloudinary HTTPS URL of the uploaded image, or `null` for list input |

#### Error Responses

| Status | Code | Trigger |
|---|---|---|
| `400` | `INVALID_IMAGE` | No image or list provided; unsupported MIME type or extension; file > 10 MB; corrupt magic bytes; invalid Base64 data URI prefix |
| `400` | `INVALID_INGREDIENTS` | Non-string items in array; more than 50 items; malformed `user_preferences` |
| `422` | `NO_INGREDIENTS_FOUND` | OpenAI vision model detected no food ingredients in the image |
| `429` | `RATE_LIMIT_EXCEEDED` | Rate limit exceeded |
| `500` | `AI_SERVICE_ERROR` | OpenAI API failure after 2 retries |
| `500` | `STORAGE_ERROR` | Cloudinary upload failed |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected error |

---

### 3. `POST /api/recipes/generate`

Generates **3 to 5 personalized recipes** from a list of ingredients using the configured OpenAI generation model.

**Rate Limit:** `aiEndpointLimiter`  
**Authentication:** None  
**Content-Type:** `application/json`  
**Cache:** Redis 1-hour TTL

#### Middleware chain

```
aiEndpointLimiter
  → validateGenerateRecipes     (Joi validation + ingredient sanitization + preference normalization)
  → generateRecipesHandler
```

#### Ingredient Sanitization

Before validation, each ingredient string is sanitized:
1. Trim leading/trailing whitespace
2. Remove all characters except `[a-zA-Z0-9 \-']`
3. Collapse multiple consecutive spaces to one
4. Truncate to 100 characters
5. Drop empty strings

If **all** ingredients reduce to empty strings after sanitization, a `400 INVALID_INGREDIENTS` error is returned.

#### Request Body

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `ingredients` | `String[]` | Yes | 1–30 items, each ≤ 100 chars after sanitization | Ingredient names |
| `user_preferences` | `Object` | No | See [user_preferences Object](#user_preferences-object) | Dietary preferences |

```json
{
  "ingredients": [
    "chicken breast",
    "broccoli",
    "rice",
    "garlic",
    "olive oil",
    "lemon",
    "soy sauce"
  ],
  "user_preferences": {
    "allergies": ["nuts"],
    "preferences": ["low-carb"],
    "dislikes": ["olives", "mushrooms"],
    "cuisines_love": ["italian", "thai", "asian"],
    "kitchen_tools": ["oven", "wok", "microwave"],
    "cooking_goals": ["reduce-food-waste", "eat-healthier-track-nutrition"],
    "time_minutes": "30-45 min",
    "servings": "2-4",
    "DNA": {
      "Spice": 60,
      "Sweet": 30,
      "Crunchy": 70
    },
    "skill_level": "HomeCook"
  }
}
```

#### Response `200 OK`

Always contains **3 to 5** recipes. The exact count is determined by the configured OpenAI generation model based on the available ingredients. Recipes are initially returned with `"image_url": null`; images are generated asynchronously in the background.

```json
{
  "success": true,
  "recipes": [
    {
      "title": "Thai Garlic Chicken with Rice",
      "recipe_name": "Thai Garlic Chicken with Rice",
      "ingredients": [
        { "name": "chicken breast", "quantity": "300", "unit": "g" },
        { "name": "rice", "quantity": "1", "unit": "cup" },
        { "name": "garlic", "quantity": "4", "unit": "cloves" },
        { "name": "soy sauce", "quantity": "2", "unit": "tbsp" },
        { "name": "olive oil", "quantity": "1", "unit": "tbsp" }
      ],
      "ingredients_to_use": [
        "300g chicken breast",
        "1 cup rice",
        "4 cloves garlic",
        "2 tbsp soy sauce",
        "1 tbsp olive oil"
      ],
      "additional_ingredients_optional": [
        "sesame oil",
        "green onions",
        "chili flakes"
      ],
      "instructions": [
        { "step": 1, "description": "Cook rice according to package instructions." },
        { "step": 2, "description": "Mince garlic and slice chicken into thin strips." },
        { "step": 3, "description": "Heat olive oil in a wok over high heat." },
        { "step": 4, "description": "Stir-fry chicken with garlic for 5 minutes until golden." },
        { "step": 5, "description": "Add soy sauce and toss to coat. Serve over rice." }
      ],
      "steps": [
        "Cook rice according to package instructions.",
        "Mince garlic and slice chicken into thin strips.",
        "Heat olive oil in a wok over high heat.",
        "Stir-fry chicken with garlic for 5 minutes until golden.",
        "Add soy sauce and toss to coat. Serve over rice."
      ],
      "cook_time": "25 minutes",
      "nutrition": {
        "calories": "420",
        "protein": "42",
        "carbs": "38",
        "fat": "10"
      },
      "image_url": null
    }
    // ... 2–4 more recipe objects
  ]
}
```

#### Error Responses

| Status | Code | Trigger |
|---|---|---|
| `400` | `INVALID_INGREDIENTS` | Missing field; not an array; empty array; > 30 items; all ingredients empty after sanitization |
| `400` | `INVALID_PREFERENCES` | Joi validation failure on `user_preferences` |
| `422` | `SCHEMA_VALIDATION_FAILED` | AI response invalid after 2 retries (wrong recipe count, missing required fields) |
| `429` | `RATE_LIMIT_EXCEEDED` | Rate limit exceeded |
| `500` | `AI_SERVICE_ERROR` | OpenAI API failure after 2 retries |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected error |

---

### 4. `POST /api/analyze`

**Primary mobile app endpoint.** Accepts a fridge/counter image, detects all food ingredients, and returns 3–5 personalized recipes — all in a single request.

Optionally accepts a `user_id` to automatically apply the user's saved dietary preferences from the database.

**Rate Limit:** `aiEndpointLimiter`  
**Authentication:** None  
**Cache:** Ingredient detection cached 24 h, recipe generation cached 1 h

#### Middleware chain

```
aiEndpointLimiter
  → upload.single('image')
  → handleUploadError
  → validateUserPreferences
  → analyzeHandler
```

#### Preference resolution order

1. If `user_id` is provided and a matching profile exists in MongoDB → use saved preferences
2. Else if `user_preferences` is provided inline → use those
3. Else → use defaults (no restrictions, default DNA)

Inline `user_preferences` in the request always take precedence over the saved profile.

---

#### Input Mode A — Multipart File Upload

**Content-Type:** `multipart/form-data`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `image` | `File` | Yes | JPEG / PNG / WebP, max 10 MB | Image file |
| `user_id` | `String` | No | — | User ID to fetch saved preferences |
| `user_preferences` | `String` | No | JSON-stringified object | Inline preferences (overrides saved profile) |

```
POST /api/analyze
Content-Type: multipart/form-data

image:            [binary file]
user_id:          user_abc123
user_preferences: {
  "allergies": ["dairy"],
  "preferences": ["vegetarian"],
  "dislikes": ["mushrooms"],
  "cuisines_love": ["italian", "mediterranean"],
  "kitchen_tools": ["oven", "blender"],
  "cooking_goals": ["reduce-food-waste"],
  "time_minutes": 30,
  "servings": "2-4",
  "DNA": { "Spice": 50, "Sweet": 40, "Crunchy": 60 },
  "skill_level": "HomeCook"
}
```

---

#### Input Mode B — Base64 Image (JSON)

**Content-Type:** `application/json`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `image_base64` | `String` | Yes | Data URI: `data:image/(jpeg\|png\|webp);base64,...` | Base64 image |
| `user_id` | `String` | No | — | User ID to fetch saved preferences |
| `user_preferences` | `Object` | No | — | Inline preferences (overrides saved profile) |

```json
{
  "image_base64": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/...",
  "user_id": "user_abc123",
  "user_preferences": {
    "allergies": ["nuts", "shellfish"],
    "preferences": ["vegetarian"],
    "dislikes": ["olives", "mushrooms"],
    "cuisines_love": ["thai", "mediterranean", "italian"],
    "kitchen_tools": ["oven", "air-fryer", "blender"],
    "cooking_goals": ["reduce-food-waste", "meal-prep-plan-week"],
    "time_minutes": "30-45 min",
    "servings": "2-4",
    "DNA": {
      "Spice": 60,
      "Sweet": 30,
      "Crunchy": 70
    },
    "skill_level": "ConfidentCook"
  }
}
```

---

#### Response `200 OK`

```json
{
  "success": true,
  "user_id": "user_abc123",
  "allowed_ingredients": [
    { "name": "chicken breast", "confidence": 0.96 },
    { "name": "broccoli", "confidence": 0.93 },
    { "name": "garlic", "confidence": 0.91 },
    { "name": "olive oil", "confidence": 0.88 },
    { "name": "lemon", "confidence": 0.84 }
  ],
  "restricted_ingredients": [
    { "ingredient": "cashews", "reason": "Allergic to nuts" },
    { "ingredient": "shrimp", "reason": "Allergic to shellfish" }
  ],
  "image_url": "https://res.cloudinary.com/your-cloud/image/upload/v1234567890/ai-recipe-app/ingredients/xyz789.jpg",
  "recipes": [
    {
      "title": "Lemon Garlic Broccoli Chicken",
      "recipe_name": "Lemon Garlic Broccoli Chicken",
      "ingredients": [
        { "name": "chicken breast", "quantity": "400", "unit": "g" },
        { "name": "broccoli", "quantity": "2", "unit": "cups" },
        { "name": "garlic", "quantity": "5", "unit": "cloves" },
        { "name": "olive oil", "quantity": "2", "unit": "tbsp" },
        { "name": "lemon", "quantity": "1", "unit": "whole" }
      ],
      "ingredients_to_use": [
        "400g chicken breast",
        "2 cups broccoli",
        "5 cloves garlic",
        "2 tbsp olive oil",
        "1 whole lemon"
      ],
      "additional_ingredients_optional": [
        "red pepper flakes",
        "fresh parsley",
        "parmesan cheese"
      ],
      "instructions": [
        { "step": 1, "description": "Preheat oven to 200°C (400°F)." },
        { "step": 2, "description": "Mince garlic and zest the lemon." },
        { "step": 3, "description": "Coat chicken with olive oil, garlic, lemon zest and juice." },
        { "step": 4, "description": "Roast chicken for 20 minutes, then add broccoli to the pan." },
        { "step": 5, "description": "Roast a further 12 minutes until broccoli is tender and chicken is golden." }
      ],
      "steps": [
        "Preheat oven to 200°C (400°F).",
        "Mince garlic and zest the lemon.",
        "Coat chicken with olive oil, garlic, lemon zest and juice.",
        "Roast chicken for 20 minutes, then add broccoli to the pan.",
        "Roast a further 12 minutes until broccoli is tender and chicken is golden."
      ],
      "cook_time": "35 minutes",
      "nutrition": {
        "calories": "380",
        "protein": "46",
        "carbs": "10",
        "fat": "16"
      },
      "image_url": null
    }
    // ... 2–4 more recipe objects
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `success` | `Boolean` | Always `true` |
| `user_id` | `String` \| `null` | Echoed back if provided in the request |
| `allowed_ingredients` | `Array` | Detected ingredients not blocked by restrictions |
| `restricted_ingredients` | `Array` | Excluded ingredients with reasons |
| `image_url` | `String` | Cloudinary HTTPS URL of the uploaded image |
| `recipes` | `Array` | 3–5 recipe objects. See [Recipe Object](#recipe-object) |

#### Error Responses

| Status | Code | Trigger |
|---|---|---|
| `400` | `INVALID_IMAGE` | No image provided; unsupported format; file > 10 MB; corrupt bytes |
| `400` | `INVALID_PREFERENCES` | Malformed `user_preferences` |
| `422` | `NO_INGREDIENTS_FOUND` | No food ingredients detected in image |
| `422` | `SCHEMA_VALIDATION_FAILED` | AI recipe response invalid after 2 retries |
| `429` | `RATE_LIMIT_EXCEEDED` | Rate limit exceeded |
| `500` | `AI_SERVICE_ERROR` | OpenAI API failure |
| `500` | `STORAGE_ERROR` | Cloudinary upload failed |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected error |

---

### 5. `GET /api/users/:userId/preferences`

Retrieve the saved dietary preference profile for a user. Always returns `200` — new or unknown users receive an empty profile (no `404`).

**Rate Limit:** `apiRateLimiter`  
**Authentication:** None

#### Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | `String` | Yes | Unique user identifier (any non-empty string) |

#### Response `200 OK`

```json
{
  "success": true,
  "user_id": "user_abc123",
  "allergies": ["dairy", "nuts"],
  "preferences": ["vegetarian", "low-carb"],
  "dislikes": ["mushrooms", "olives"],
  "cuisines_love": ["italian", "pakistani", "thai"],
  "kitchen_tools": ["oven", "gas-burner", "blender", "air-fryer"],
  "cooking_goals": ["reduce-food-waste", "meal-prep-plan-week", "eat-healthier-track-nutrition"],
  "DNA": {
    "Spice": 60,
    "Sweet": 30,
    "Crunchy": 70
  },
  "skill_level": {
    "label": "HomeCook",
    "percent": 45
  },
  "time_minutes": {
    "min": 20,
    "max": 45
  },
  "servings": {
    "min": 2,
    "max": 4
  }
}
```

**For a new / unknown user:**

```json
{
  "success": true,
  "user_id": "new_user_999",
  "allergies": [],
  "preferences": [],
  "dislikes": [],
  "cuisines_love": [],
  "kitchen_tools": [],
  "cooking_goals": [],
  "DNA": { "Spice": 50, "Sweet": 50, "Crunchy": 50 },
  "skill_level": { "label": null, "percent": 50 },
  "time_minutes": null,
  "servings": null
}
```

| Field | Type | Description |
|---|---|---|
| `user_id` | `String` | User identifier |
| `allergies` | `String[]` | Hard-excluded ingredients |
| `preferences` | `String[]` | Dietary preferences |
| `dislikes` | `String[]` | Soft-excluded ingredients |
| `cuisines_love` | `String[]` | Preferred cuisine types (normalized slugs) |
| `kitchen_tools` | `String[]` | Available equipment (normalized slugs) |
| `cooking_goals` | `String[]` | Cooking goals (normalized slugs) |
| `DNA` | `Object` | Flavor profile. Keys: dimension names, Values: `0–100` |
| `skill_level` | `Object` | `{ label: String\|null, percent: Number 0–100 }` |
| `time_minutes` | `Number` \| `Object` \| `null` | Cooking time. `null` = no constraint, `number` = exact minutes, `{min, max}` = range |
| `servings` | `Object` \| `null` | `null` = varies, `{ min: Number, max: Number\|null }` |

#### Error Responses

| Status | Code | Trigger |
|---|---|---|
| `400` | `INVALID_USER_ID` | `userId` missing or empty after trim |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected error |

---

### 6. `PUT /api/users/:userId/preferences`

Create or fully replace the dietary preference profile for a user. Performs a MongoDB **upsert** — creates a new document if the user does not exist, or replaces all preference fields if they do.

**Rate Limit:** `apiRateLimiter`  
**Authentication:** None  
**Content-Type:** `application/json`

#### Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | `String` | Yes | Unique user identifier |

#### Request Body

All fields are optional. Any omitted field is reset to its default (empty array / default DNA). Unknown fields are silently stripped.

```json
{
  "allergies": ["dairy", "nuts", "shellfish"],
  "preferences": ["vegetarian", "low-carb"],
  "dislikes": ["olives", "mushrooms", "celery"],
  "cuisines_love": ["italian", "pakistani", "thai", "mediterranean"],
  "kitchen_tools": ["oven", "instant-pot", "air-fryer", "blender"],
  "cooking_goals": [
    "reduce-food-waste",
    "meal-prep-plan-week",
    "eat-healthier-track-nutrition",
    "cook-more-at-home-save-money"
  ],
  "DNA": {
    "Spice": 60,
    "Sweet": 25,
    "Crunchy": 75,
    "Umami": 80
  },
  "skill_level": "ConfidentCook",
  "time_minutes": "30-45 min",
  "servings": "2-4"
}
```

All [field aliases](#field-name-aliases) are accepted here too.

#### Validation Rules

| Field | Rule |
|---|---|
| `allergies` | Array of strings, each ≤ 50 chars |
| `preferences` | Array of strings, each ≤ 50 chars |
| `dislikes` | Array of strings, each ≤ 50 chars |
| `cuisines_love` | Array or comma-separated string; each item ≤ 50 chars; normalized to slugs |
| `kitchen_tools` | Array or comma-separated string; each item ≤ 50 chars; normalized to slugs |
| `cooking_goals` | Array or comma-separated string; each item ≤ 80 chars; normalized to slugs |
| `DNA` | Object with string keys and numeric values `0–100` |
| `skill_level` | String enum, number `0–100`, or `{ label, percent }` object |
| `time_minutes` | Flexible format (see [`time_minutes` formats](#time_minutes-accepted-formats)) |
| `servings` | Flexible format (see [`servings` formats](#servings-accepted-formats)); max 50 |

#### Response `200 OK`

Returns the full normalized saved profile (same shape as `GET`):

```json
{
  "success": true,
  "user_id": "user_abc123",
  "allergies": ["dairy", "nuts", "shellfish"],
  "preferences": ["vegetarian", "low-carb"],
  "dislikes": ["olives", "mushrooms", "celery"],
  "cuisines_love": ["italian", "pakistani", "thai", "mediterranean"],
  "kitchen_tools": ["oven", "instant-pot", "air-fryer", "blender"],
  "cooking_goals": [
    "reduce-food-waste",
    "meal-prep-plan-week",
    "eat-healthier-track-nutrition",
    "cook-more-at-home-save-money"
  ],
  "DNA": {
    "Spice": 60,
    "Sweet": 25,
    "Crunchy": 75,
    "Umami": 80
  },
  "skill_level": {
    "label": "ConfidentCook",
    "percent": 70
  },
  "time_minutes": {
    "min": 30,
    "max": 45
  },
  "servings": {
    "min": 2,
    "max": 4
  }
}
```

#### Error Responses

| Status | Code | Trigger |
|---|---|---|
| `400` | `INVALID_USER_ID` | `userId` missing or empty after trim |
| `400` | `INVALID_PREFERENCES` | Joi validation failure; `time_minutes` or `servings` in unrecognized format; `DNA` value out of range |
| `500` | `STORAGE_ERROR` | MongoDB upsert failed |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected error |

---

### 7. `DELETE /api/users/:userId/preferences`

Remove a user's stored preference profile. Idempotent — returns `200` success even if the user profile did not exist.

**Rate Limit:** `apiRateLimiter`  
**Authentication:** None

#### Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `userId` | `String` | Yes | Unique user identifier |

#### Response `200 OK`

```json
{
  "success": true,
  "user_id": "user_abc123",
  "message": "User preferences deleted."
}
```

#### Error Responses

| Status | Code | Trigger |
|---|---|---|
| `400` | `INVALID_USER_ID` | `userId` missing or empty after trim |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected error |

---

## AI Models & Prompts

### gpt-5 — Ingredient Detection

**Model:** `gpt-5`  
**Used in:** `/api/ingredients/detect` (image and URL modes), `/api/analyze`  
**Settings:** `max_tokens: 1024`, `temperature: 0.1`, `response_format: { type: 'json_object' }`  
**Retries:** 1 automatic retry on failure

**System prompt summary:**
- "You are a culinary AI assistant specialised in identifying food ingredients."
- Analyze fridge, pantry, or counter images
- Identify ALL visible food ingredients (lowercase, common English names)
- Confidence scores `0.0–1.0`
- Categorize into `allowed_ingredients` (no restrictions) and `restricted_ingredients` (blocked by allergies/preferences)
- `dislikes` are **not** applied at detection stage — only soft-avoided in recipe generation
- Return **only** valid JSON, no prose, no markdown

**User message:**
> "Please analyse this image and list ALL food ingredients you can see."

**Response format required from model:**
```json
{
  "allowed_ingredients": [
    { "name": "tomato", "confidence": 0.97 }
  ],
  "restricted_ingredients": [
    { "ingredient": "butter", "reason": "Allergic to dairy" }
  ]
}
```

---

### gpt-5-mini — Pre-detected List Filtering

**Model:** `gpt-5-mini`  
**Used in:** `/api/ingredients/detect` (list mode, only when restrictions exist)  
**Settings:** `temperature: 0.1`, `response_format: { type: 'json_object' }`

**System prompt summary:**
- "Categorize these ingredients based on the user's dietary restrictions."
- Hard skip: never return allergen ingredients in allowed list
- Preference-soft skip: move items that violate preferences to restricted

---

### gpt-5-mini — Recipe Generation

**Model:** `gpt-5-mini`  
**Used in:** `/api/recipes/generate`, `/api/analyze`  
**Settings:** `max_tokens: 4096`, `temperature: 0.7`, `response_format: { type: 'json_object' }`  
**Retries:** Up to 2 attempts (1 normal + 1 schema-retry with reinforced instructions)

**System prompt key constraints:**
- Generate **between 3 and 5 recipes** (strictly enforced)
- **CRITICAL:** Never include allergens in any recipe
- Apply dietary preferences, avoid dislikes as main ingredients
- Match cuisine style preferences
- Use only tools available in user's kitchen
- Honor cooking goals
- Bias flavor profile per DNA scores (0 = none, 100 = maximum)
- Adjust complexity per skill level:
  - `total_beginer` (< 20%) → minimal techniques, simple steps
  - `HomeCook` (20–50%) → moderate complexity
  - `ConfidentCook` (50–75%) → advanced techniques welcome
  - `Advanced Semi Pro` (> 75%) → professional methods
- Respect time and servings constraints
- Estimate calories — always provide a value, never null for calories
- All required fields must be non-empty

**User message:**
> "Generate between 3 and 5 recipes I can cook using these available ingredients: [comma-separated list]"

**Required response format:**
```json
{
  "recipes": [
    {
      "title": "...",
      "ingredients": [
        { "name": "...", "quantity": "...", "unit": "..." }
      ],
      "additional_ingredients_optional": ["..."],
      "instructions": [
        { "step": 1, "description": "..." }
      ],
      "cook_time": "...",
      "nutrition": {
        "calories": "...",
        "protein": "... or null",
        "carbs": "... or null",
        "fat": "... or null"
      }
    }
  ]
}
```

---

### DALL·E 3 — Recipe Food Photography

**Model:** `dall-e-3`  
**Used in:** Background task after recipe generation  
**Settings:** `size: "1024x1024"`, `quality: "standard"`, `response_format: "b64_json"`  
**Non-blocking:** Failures are logged and silently ignored — never block the HTTP response

**Prompt template:**
> "Professional food photography of `[recipe_title]`, plated beautifully on a rustic ceramic plate, top-down overhead angle, soft natural window lighting, shallow depth of field, appetising, high resolution, vibrant colours, no text, no watermarks, no people"

Generated images are uploaded to Cloudinary and the resulting HTTPS URL is patched into both Redis cache and MongoDB. Subsequent reads of those recipes will include the real `image_url`.

---

## Background Image Generation

After recipes are generated and returned to the client (with `image_url: null`), a background fire-and-forget task runs:

```
recipes returned to client (image_url: null)
    │
    └── background task (non-blocking)
          │
          ├── For each recipe in parallel:
          │     ├── OpenAI image model → base64 image
          │     └── Upload to Cloudinary → get HTTPS URL
          │
          ├── Update Redis cache (patch image_url into cached recipe array)
          └── Update MongoDB documents (set image_url field by _id)

Next request that hits cache → recipes now include real image_url
```

If OpenAI image generation or Cloudinary fails for any recipe, that recipe's `image_url` stays `null` permanently for that cache entry. The failure is logged as a warning.

---

## Database Schemas

### `users` Collection

```
user_id        String    required, unique, indexed
allergies      [String]  default: []
preferences    [String]  default: []
dislikes       [String]  default: []
cuisines_love  [String]  default: [], normalized slugs
kitchen_tools  [String]  default: [], normalized slugs
cooking_goals  [String]  default: [], normalized slugs
DNA            Map<String, Number 0-100>   default: { Spice:50, Sweet:50, Crunchy:50 }
skill_level    {
  label:   String|null   enum: null | 'total_beginer' | 'HomeCook' | 'ConfidentCook' | 'Advanced Semi Pro'
  percent: Number 0-100
}
time_minutes   Mixed     null | Number | { min: Number, max: Number }
servings       Mixed     null | { min: Number, max: Number|null }
created_at     Date
updated_at     Date
```

**Indexes:** `user_id` (unique)

---

### `recipes` Collection

```
title                          String    required
recipe_name                    String    required
ingredients                    [{
  name:     String  required
  quantity: String  required
  unit:     String  optional
}]                             min 1 item
ingredients_to_use             [String]  min 1 item
additional_ingredients_optional [String] default: []
instructions                   [{
  step:        Number  required
  description: String  required
}]                             min 1 item
steps                          [String]  min 1 item
cook_time                      String    required
nutrition                      {
  calories: String|null
  protein:  String|null
  carbs:    String|null
  fat:      String|null
}
source_ingredients             [String]  lowercase, indexed
image_url                      String|null   Cloudinary URL (populated async)
created_at                     Date
updated_at                     Date
```

**Indexes:**
- `source_ingredients` — for ingredient-based recipe lookup
- `created_at: -1` — for recent-first sorting

---

## Startup & Graceful Shutdown

### Startup sequence (`server.js`)

1. Override DNS servers to `8.8.8.8` / `8.8.4.4` — workaround for Node.js v24 Windows c-ares DNS bug that breaks `mongodb+srv://` connection strings
2. Connect to MongoDB with connection pooling (`maxPoolSize: 10`)
3. Initialize Redis client (lazy — connects on first use)
4. Create Express app instance
5. Start HTTP server on configured port
6. Set server-level request timeout to **35 seconds**
7. Register `SIGTERM` / `SIGINT` signal handlers

### Graceful shutdown

1. `server.close()` — stop accepting new connections, drain existing
2. Close MongoDB connection pool
3. Close Redis connection
4. Exit with code `0` (clean) or `1` (error)
5. Force-exit after **15 seconds** if shutdown stalls

---

## API Documentation (Swagger)

| URL | Description |
|---|---|
| `GET /api/docs` | Interactive Swagger UI |
| `GET /api/docs.json` | Raw OpenAPI 3.0.3 specification (JSON) |

Enabled automatically in all non-production environments. In production, set `ENABLE_DOCS=true`.

---

## All Endpoints — Quick Reference

| Method | Path | Rate Limiter | Auth | Description |
|---|---|---|---|---|
| `GET` | `/health` | `apiRateLimiter` | None | Server health check |
| `POST` | `/api/ingredients/detect` | `aiEndpointLimiter` | None | Detect ingredients from image / Base64 / URL / list |
| `POST` | `/api/recipes/generate` | `aiEndpointLimiter` | None | Generate 3–5 recipes from ingredient list |
| `POST` | `/api/analyze` | `aiEndpointLimiter` | None | Combined fridge scan + recipe generation |
| `GET` | `/api/users/:userId/preferences` | `apiRateLimiter` | None | Get saved dietary preferences |
| `PUT` | `/api/users/:userId/preferences` | `apiRateLimiter` | None | Save / replace dietary preferences |
| `DELETE` | `/api/users/:userId/preferences` | `apiRateLimiter` | None | Delete dietary preferences |

---

## Project Structure

```
src/
├── server.js                        # Entry point — DNS fix, HTTP server, graceful shutdown
├── app.js                           # Express app — middleware stack, route mounting
│
├── config/
│   ├── index.js                     # Centralised config (env vars, defaults, validation)
│   ├── db.js                        # MongoDB connection + event handlers
│   ├── redis.js                     # ioredis singleton + lazy init
│   ├── openai.js                    # OpenAI SDK singleton (timeout: 30s, maxRetries: 0)
│   ├── storage.js                   # Cloudinary SDK init
│   └── swagger.js                   # OpenAPI 3.0.3 spec + Swagger UI setup
│
├── routes/
│   ├── ingredientRoutes.js          # POST /api/ingredients/detect
│   ├── recipeRoutes.js              # POST /api/recipes/generate
│   ├── analyzeRoutes.js             # POST /api/analyze
│   └── userRoutes.js                # GET|PUT|DELETE /api/users/:userId/preferences
│
├── controllers/
│   ├── ingredientController.js      # detectIngredientsHandler — input routing + response shaping
│   ├── recipeController.js          # generateRecipesHandler
│   ├── analyzeController.js         # analyzeHandler — orchestrates detect + generate pipeline
│   └── userController.js           # getUserPreferences, upsertUserPreferences, deleteUserPreferences
│
├── validators/
│   ├── ingredientValidator.js       # isValidImageBuffer, validateImageUpload, validateUserPreferences
│   └── recipeValidator.js           # validateGenerateRecipes (Joi + sanitiseIngredients)
│
├── models/
│   ├── User.js                      # users collection schema
│   └── Recipe.js                    # recipes collection schema
│
├── middleware/
│   ├── errorHandler.js              # AppError class, global errorHandler, asyncHandler wrapper
│   ├── rateLimiter.js               # apiRateLimiter, aiEndpointLimiter
│   └── upload.js                    # Multer memory storage + handleUploadError
│
├── services/
│   ├── aiService.js                 # All OpenAI calls — vision, generation, and image models
│   ├── ingredientService.js         # Ingredient detection orchestration + caching
│   ├── recipeService.js             # Recipe generation orchestration + caching + background images
│   ├── userService.js               # User CRUD — getUserPreferences, upsertUserPreferences, deleteUserPreferences
│   ├── cacheService.js              # Redis get/set helpers with silent error handling
│   └── storageService.js            # Cloudinary upload via streamifier
│
└── utils/
    ├── dna.js                       # Preference normalization — DNA, skill level, time, servings, slugs
    ├── hash.js                      # SHA-256 helpers — hashBuffer, hashIngredients, sanitiseIngredient(s)
    └── logger.js                    # Winston logger (JSON in prod, pretty in dev)
```

## KILL 

```bash
$conns = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue; if ($conns) { $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique; Stop-Process -Id $pids -Force; "Stopped PID(s): $($pids -join ', ')" } else { "No process is listening on port 3000." }
```