# Spec Compliance Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close two spec compliance gaps: (1) add `image_url` string input support to `/api/ingredients/detect`, and (2) relax recipe count validation from exactly 5 to between 3 and 5.

**Architecture:** Fix 1 adds a new `detectIngredientsFromUrl()` function in `ingredientService.js` that skips the Cloudinary upload (URL is already public) and passes it directly to GPT-4o, then wires it into the controller as a new input branch. Fix 2 changes three string literals and one integer comparison in `aiService.js`.

**Tech Stack:** Node.js, Express, Mongoose, OpenAI SDK, Redis (ioredis), Cloudinary

---

## Files Modified

- `src/controllers/ingredientController.js` — add `image_url` branch (Case 1)
- `src/services/ingredientService.js` — add `detectIngredientsFromUrl()` function
- `src/services/aiService.js` — change prompt text and schema validation count from exactly 5 to 3–5

---

## Task 1: Add `detectIngredientsFromUrl()` to ingredientService

**Files:**
- Modify: `src/services/ingredientService.js`

- [ ] **Step 1: Add the new function at the bottom of ingredientService.js, before `module.exports`**

Open `src/services/ingredientService.js` and replace the `module.exports` line with:

```js
/**
 * Runs ingredient detection for a pre-existing public image URL.
 * Skips Cloudinary upload since the URL is already publicly accessible.
 * Cache key is derived from the URL string (not image bytes).
 *
 * @param {string} imageUrl - Publicly accessible image URL
 * @param {object} userPreferences - { allergies: string[], preferences: string[] }
 * @returns {Promise<{allowed_ingredients: Array, restricted_ingredients: Array, image_url: string}>}
 * @throws {AppError} 422 NO_INGREDIENTS_FOUND | 500 AI_SERVICE_ERROR
 */
async function detectIngredientsFromUrl(imageUrl, userPreferences = {}) {
  // Cache key: hash of the URL string + preferences
  const cacheHash = hashIngredients([imageUrl], userPreferences);
  const cacheKey = ingredientCacheKey(`url:${cacheHash}`);

  const cached = await getCache(cacheKey);
  if (cached) {
    logger.debug(`Cache HIT for image URL key: ${cacheKey}`);
    return cached;
  }
  logger.debug(`Cache MISS for image URL key: ${cacheKey}`);

  // Call GPT-4o vision directly with the provided URL
  const resultData = await detectIngredientsFromImage(imageUrl, userPreferences);

  if (resultData.allowed_ingredients.length === 0 && resultData.restricted_ingredients.length === 0) {
    throw new AppError(
      422,
      'NO_INGREDIENTS_FOUND',
      'The vision model could not detect any food ingredients in the provided image URL'
    );
  }

  const result = { ...resultData, image_url: imageUrl };
  await setCache(cacheKey, result, config.redis.ingredientTTL);

  return result;
}

module.exports = { detectIngredients, detectIngredientsFromList, detectIngredientsFromUrl };
```

- [ ] **Step 2: Verify the file looks correct**

Run:
```bash
node -e "const s = require('./src/services/ingredientService'); console.log(Object.keys(s))"
```
Expected output:
```
[ 'detectIngredients', 'detectIngredientsFromList', 'detectIngredientsFromUrl' ]
```

- [ ] **Step 3: Commit**

```bash
git add src/services/ingredientService.js
git commit -m "feat: add detectIngredientsFromUrl for image_url input support"
```

---

## Task 2: Wire `image_url` into the ingredient controller (Case 1)

**Files:**
- Modify: `src/controllers/ingredientController.js`

- [ ] **Step 1: Update the import at the top of the controller**

Replace:
```js
const { detectIngredients, detectIngredientsFromList } = require('../services/ingredientService');
```
With:
```js
const { detectIngredients, detectIngredientsFromList, detectIngredientsFromUrl } = require('../services/ingredientService');
```

- [ ] **Step 2: Add the `image_url` branch in the handler**

In `detectIngredientsHandler`, find the comment `// Case 1 & 2: Image buffer` and replace the entire block from that comment to the end of the `else if (req.file)` / `else` block with:

```js
  // Case 1: Pre-existing public image URL
  if (req.body.image_url) {
    const imageUrl = String(req.body.image_url).trim();
    if (!imageUrl.startsWith('http://') && !imageUrl.startsWith('https://')) {
      throw new AppError(400, 'INVALID_IMAGE', 'image_url must be a valid http or https URL.');
    }
    logger.info(`Ingredient detection request — image_url: ${imageUrl}`);
    const startTime = Date.now();
    const result = await detectIngredientsFromUrl(imageUrl, userPreferences);
    const elapsed = Date.now() - startTime;
    logger.info(`Ingredient detection (URL) completed in ${elapsed}ms — allowed: ${result.allowed_ingredients.length}`);
    return res.status(200).json({ success: true, ...result });
  }

  // Case 2 & 3: Image buffer (base64 or file upload)
  let buffer;
  let originalname;

  if (req.body.image_base64) {
    logger.info(`Ingredient detection request — base64 image`);

    const match = req.body.image_base64.match(/^data:image\/(jpeg|png|webp);base64,/);
    if (!match) {
      throw new AppError(400, 'INVALID_IMAGE', 'Base64 image must have a valid data URI prefix (jpeg, png, or webp).');
    }

    const base64Data = req.body.image_base64.replace(/^data:image\/\w+;base64,/, '');
    buffer = Buffer.from(base64Data, 'base64');

    if (buffer.byteLength > config.upload.maxFileSizeBytes) {
      throw new AppError(400, 'INVALID_IMAGE', `Image size exceeds the 10 MB limit (decoded size: ${(buffer.byteLength / 1024 / 1024).toFixed(2)} MB).`);
    }

    if (!isValidImageBuffer(buffer)) {
      throw new AppError(400, 'INVALID_IMAGE', 'Base64 data does not contain a valid JPEG, PNG, or WebP image.');
    }

    originalname = `base64_upload_${Date.now()}.${match[1]}`;
  } else if (req.file) {
    buffer = req.file.buffer;
    originalname = req.file.originalname;
    logger.info(`Ingredient detection request — file: ${originalname} (${req.file.size} bytes)`);
  } else {
    throw new AppError(400, 'INVALID_IMAGE', 'No image file, base64 data, image_url, or ingredients array provided.');
  }

  const startTime = Date.now();
  const result = await detectIngredients(buffer, originalname, userPreferences);
  const elapsed = Date.now() - startTime;

  logger.info(`Ingredient detection completed in ${elapsed}ms — allowed: ${result.allowed_ingredients.length}, restricted: ${result.restricted_ingredients.length}`);

  return res.status(200).json({
    success: true,
    ...result,
  });
```

- [ ] **Step 3: Verify the server starts without errors**

```bash
node -e "require('./src/controllers/ingredientController'); console.log('OK')"
```
Expected: `OK`

- [ ] **Step 4: Commit**

```bash
git add src/controllers/ingredientController.js
git commit -m "feat: support image_url input on POST /api/ingredients/detect (spec Case 1)"
```

---

## Task 3: Relax recipe count from exactly 5 to 3–5 in aiService

**Files:**
- Modify: `src/services/aiService.js`

- [ ] **Step 1: Update `buildRecipeSystemPrompt` — change "EXACTLY 5" to "between 3 and 5"**

Find and replace in `src/services/aiService.js`:

Replace:
```js
You will receive a list of available ingredients (from a user's fridge, pantry, or kitchen) and must generate EXACTLY 5 distinct, practical recipes the user can cook with those ingredients.
```
With:
```js
You will receive a list of available ingredients (from a user's fridge, pantry, or kitchen) and must generate between 3 and 5 distinct, practical recipes the user can cook with those ingredients.
```

- [ ] **Step 2: Update the IMPORTANT RULES bullet in the same prompt**

Replace:
```js
- Generate EXACTLY 5 recipes — no more, no fewer
```
With:
```js
- Generate between 3 and 5 recipes — at least 3, no more than 5
```

- [ ] **Step 3: Update `SCHEMA_RETRY_SUFFIX`**

Replace:
```js
- "recipes" array containing EXACTLY 5 items (not 3, not 4, not 6 — exactly 5)
```
With:
```js
- "recipes" array containing between 3 and 5 items (minimum 3, maximum 5)
```

- [ ] **Step 4: Update the user message in `generateRecipesFromIngredients`**

Find:
```js
content: `Generate exactly 5 recipes I can cook using these available ingredients: ${ingredientList}`,
```
Replace with:
```js
content: `Generate between 3 and 5 recipes I can cook using these available ingredients: ${ingredientList}`,
```

- [ ] **Step 5: Update `validateRecipesSchema` — change the count check**

Find:
```js
  if (parsed.recipes.length !== 5) {
    throw new AppError(
      422,
      'SCHEMA_VALIDATION_FAILED',
      `Expected exactly 5 recipes but got ${parsed.recipes.length}`
    );
  }
```
Replace with:
```js
  if (parsed.recipes.length < 3 || parsed.recipes.length > 5) {
    throw new AppError(
      422,
      'SCHEMA_VALIDATION_FAILED',
      `Expected between 3 and 5 recipes but got ${parsed.recipes.length}`
    );
  }
```

- [ ] **Step 6: Verify the file parses without errors**

```bash
node -e "require('./src/services/aiService'); console.log('OK')"
```
Expected: `OK`

- [ ] **Step 7: Commit**

```bash
git add src/services/aiService.js
git commit -m "fix: relax recipe count validation from exactly 5 to 3-5 per spec"
```

---

## Task 4: Smoke test both fixes with the running server

- [ ] **Step 1: Start the server in a separate terminal**

```bash
npm run dev
```
Wait for: `AI Recipe API server running on port 3000`

- [ ] **Step 2: Test `image_url` input — valid URL**

```bash
curl -s -X POST http://localhost:3000/api/ingredients/detect \
  -H "Content-Type: application/json" \
  -d '{"image_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/1200px-Cat03.jpg"}' | node -e "const d=require('fs').readFileSync('/dev/stdin','utf8'); const j=JSON.parse(d); console.log('success:', j.success, '| keys:', Object.keys(j).join(', '))"
```
Expected: `success: true | keys: success, allowed_ingredients, restricted_ingredients, ...`
(May return empty ingredients since it's not a food image — that's fine, what matters is it reaches the AI without a 400/500)

- [ ] **Step 3: Test `image_url` input — invalid URL (no http/https)**

```bash
curl -s -X POST http://localhost:3000/api/ingredients/detect \
  -H "Content-Type: application/json" \
  -d '{"image_url": "ftp://bad-url.com/img.jpg"}' | node -e "const d=require('fs').readFileSync('/dev/stdin','utf8'); const j=JSON.parse(d); console.log('code:', j.error?.code)"
```
Expected: `code: INVALID_IMAGE`

- [ ] **Step 4: Verify existing inputs still work — missing image**

```bash
curl -s -X POST http://localhost:3000/api/ingredients/detect \
  -H "Content-Type: application/json" \
  -d '{}' | node -e "const d=require('fs').readFileSync('/dev/stdin','utf8'); const j=JSON.parse(d); console.log('code:', j.error?.code)"
```
Expected: `code: INVALID_IMAGE`
