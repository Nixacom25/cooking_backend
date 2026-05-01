# Dislikes Preference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `dislikes` field to `user_preferences` — a soft-avoid list that instructs the AI to exclude those ingredients from recipes where possible, unlike `allergies` which hard-blocks them.

**Architecture:** `dislikes` flows through every layer that `allergies` and `preferences` already flow through: Joi validators → MongoDB model → user service → AI prompts. In ingredient detection, dislikes do NOT filter ingredients (items in the fridge are still detected). In recipe generation, dislikes produce a soft-avoid clause in the GPT-4o-mini prompt. The User model gains a `dislikes` field, and all CRUD endpoints return it.

**Tech Stack:** Node.js, Express, Mongoose, Joi, OpenAI SDK (GPT-4o-mini prompt), Python pytest (test suite)

---

## Files Modified

- `src/models/User.js` — add `dislikes: [String]` field
- `src/validators/ingredientValidator.js` — add `dislikes` to `userPreferencesSchema`
- `src/validators/recipeValidator.js` — add `dislikes` to `user_preferences` Joi schema
- `src/controllers/userController.js` — add `dislikes` to `preferencesBodySchema` + all responses
- `src/services/userService.js` — pass `dislikes` through upsert and get
- `src/services/aiService.js` — add dislikes clause to `buildRecipeSystemPrompt`
- `tests/utils/api_client.py` — add `dislikes` param to `set_user_preferences`
- `tests/utils/validators.py` — add `dislikes` to `assert_user_preferences_response`
- `tests/unit/test_user_preferences.py` — add dislikes-specific tests
- `tests/AI_Recipe_API.postman_collection.json` — update example bodies

---

## Task 1: Add `dislikes` to the MongoDB User model

**Files:**
- Modify: `src/models/User.js`

- [ ] **Step 1: Add the `dislikes` field to `UserSchema`**

In `src/models/User.js`, replace:
```js
    preferences: {
      type: [String],
      default: [],
    },
  },
```
With:
```js
    preferences: {
      type: [String],
      default: [],
    },

    /**
     * Disliked ingredients — soft avoid in recipe generation.
     * The AI will try to exclude these but may use them as optional ingredients.
     * Example: ['mushrooms', 'olives']
     */
    dislikes: {
      type: [String],
      default: [],
    },
  },
```

- [ ] **Step 2: Verify the model loads without errors**

```bash
node -e "const U = require('./src/models/User'); console.log(Object.keys(U.schema.paths))"
```
Expected output includes: `dislikes`

---

## Task 2: Add `dislikes` to the ingredient validator (inline user_preferences)

**Files:**
- Modify: `src/validators/ingredientValidator.js`

- [ ] **Step 1: Add `dislikes` to `userPreferencesSchema`**

In `src/validators/ingredientValidator.js`, replace:
```js
const userPreferencesSchema = Joi.object({
  allergies: Joi.array().items(Joi.string().max(50)).default([]),
  preferences: Joi.array().items(Joi.string().max(50)).default([]),
}).default({ allergies: [], preferences: [] });
```
With:
```js
const userPreferencesSchema = Joi.object({
  allergies:   Joi.array().items(Joi.string().max(50)).default([]),
  preferences: Joi.array().items(Joi.string().max(50)).default([]),
  dislikes:    Joi.array().items(Joi.string().max(50)).default([]),
}).default({ allergies: [], preferences: [], dislikes: [] });
```

- [ ] **Step 2: Verify the validator module loads cleanly**

```bash
node -e "require('./src/validators/ingredientValidator'); console.log('OK')"
```
Expected: `OK`

---

## Task 3: Add `dislikes` to the recipe validator

**Files:**
- Modify: `src/validators/recipeValidator.js`

- [ ] **Step 1: Add `dislikes` to `user_preferences` inside `generateRecipesSchema`**

In `src/validators/recipeValidator.js`, replace:
```js
  user_preferences: Joi.object({
    allergies: Joi.array().items(Joi.string().max(50)).default([]),
    preferences: Joi.array().items(Joi.string().max(50)).default([]),
  }).default({ allergies: [], preferences: [] }),
```
With:
```js
  user_preferences: Joi.object({
    allergies:   Joi.array().items(Joi.string().max(50)).default([]),
    preferences: Joi.array().items(Joi.string().max(50)).default([]),
    dislikes:    Joi.array().items(Joi.string().max(50)).default([]),
  }).default({ allergies: [], preferences: [], dislikes: [] }),
```

- [ ] **Step 2: Verify the validator module loads cleanly**

```bash
node -e "require('./src/validators/recipeValidator'); console.log('OK')"
```
Expected: `OK`

---

## Task 4: Add `dislikes` to the user controller (CRUD endpoints)

**Files:**
- Modify: `src/controllers/userController.js`

- [ ] **Step 1: Add `dislikes` to `preferencesBodySchema`**

Replace:
```js
const preferencesBodySchema = Joi.object({
  allergies: Joi.array()
    .items(Joi.string().max(50).trim())
    .default([])
    .messages({ 'array.base': '"allergies" must be an array of strings' }),
  preferences: Joi.array()
    .items(Joi.string().max(50).trim())
    .default([])
    .messages({ 'array.base': '"preferences" must be an array of strings' }),
});
```
With:
```js
const preferencesBodySchema = Joi.object({
  allergies: Joi.array()
    .items(Joi.string().max(50).trim())
    .default([])
    .messages({ 'array.base': '"allergies" must be an array of strings' }),
  preferences: Joi.array()
    .items(Joi.string().max(50).trim())
    .default([])
    .messages({ 'array.base': '"preferences" must be an array of strings' }),
  dislikes: Joi.array()
    .items(Joi.string().max(50).trim())
    .default([])
    .messages({ 'array.base': '"dislikes" must be an array of strings' }),
});
```

- [ ] **Step 2: Add `dislikes` to `getUserPreferencesHandler` response**

Replace:
```js
  return res.status(200).json({
    success: true,
    user_id: userId,
    allergies: prefs ? prefs.allergies : [],
    preferences: prefs ? prefs.preferences : [],
  });
```
With:
```js
  return res.status(200).json({
    success: true,
    user_id: userId,
    allergies:   prefs ? prefs.allergies   : [],
    preferences: prefs ? prefs.preferences : [],
    dislikes:    prefs ? prefs.dislikes    : [],
  });
```

- [ ] **Step 3: Update the logger line in `upsertUserPreferencesHandler`**

Replace:
```js
  logger.info(
    `Preferences saved — user: ${userId}, allergies: [${saved.allergies.join(', ')}], ` +
      `preferences: [${saved.preferences.join(', ')}]`
  );
```
With:
```js
  logger.info(
    `Preferences saved — user: ${userId}, allergies: [${saved.allergies.join(', ')}], ` +
      `preferences: [${saved.preferences.join(', ')}], dislikes: [${saved.dislikes.join(', ')}]`
  );
```

- [ ] **Step 4: Verify the controller loads cleanly**

```bash
node -e "require('./src/controllers/userController'); console.log('OK')"
```
Expected: `OK`

---

## Task 5: Add `dislikes` to the user service

**Files:**
- Modify: `src/services/userService.js`

- [ ] **Step 1: Update `getUserPreferences` to return `dislikes`**

Replace:
```js
    if (!user) return null;
    return { allergies: user.allergies || [], preferences: user.preferences || [] };
```
With:
```js
    if (!user) return null;
    return {
      allergies:   user.allergies   || [],
      preferences: user.preferences || [],
      dislikes:    user.dislikes    || [],
    };
```

- [ ] **Step 2: Update `upsertUserPreferences` signature and `$set`**

Replace:
```js
async function upsertUserPreferences(userId, { allergies = [], preferences = [] }) {
  try {
    const user = await User.findOneAndUpdate(
      { user_id: userId },
      {
        $set: {
          allergies: allergies.map((a) => String(a).trim().toLowerCase()).filter(Boolean),
          preferences: preferences.map((p) => String(p).trim().toLowerCase()).filter(Boolean),
        },
      },
      { new: true, upsert: true, runValidators: true, lean: true }
    );
    logger.info(`User preferences upserted for: ${userId}`);
    return { allergies: user.allergies, preferences: user.preferences };
```
With:
```js
async function upsertUserPreferences(userId, { allergies = [], preferences = [], dislikes = [] }) {
  try {
    const user = await User.findOneAndUpdate(
      { user_id: userId },
      {
        $set: {
          allergies:   allergies.map((a) => String(a).trim().toLowerCase()).filter(Boolean),
          preferences: preferences.map((p) => String(p).trim().toLowerCase()).filter(Boolean),
          dislikes:    dislikes.map((d) => String(d).trim().toLowerCase()).filter(Boolean),
        },
      },
      { new: true, upsert: true, runValidators: true, lean: true }
    );
    logger.info(`User preferences upserted for: ${userId}`);
    return { allergies: user.allergies, preferences: user.preferences, dislikes: user.dislikes };
```

- [ ] **Step 3: Verify the service loads cleanly**

```bash
node -e "require('./src/services/userService'); console.log('OK')"
```
Expected: `OK`

---

## Task 6: Add `dislikes` soft-avoid clause to AI recipe prompt

**Files:**
- Modify: `src/services/aiService.js`

- [ ] **Step 1: Update `buildRecipeSystemPrompt` signature and add dislikes clause**

Replace:
```js
function buildRecipeSystemPrompt(allergies = [], preferences = []) {
  const allergyClause =
    allergies.length > 0
      ? `CRITICAL — the user is allergic to: ${allergies.join(', ')}. Do NOT include these ingredients in ANY recipe.`
      : 'The user has no known allergies.';

  const prefClause =
    preferences.length > 0
      ? `User dietary preferences: ${preferences.join(', ')}. Prioritise recipes that respect these preferences.`
      : '';

  return `You are an expert chef and nutritionist AI.
You will receive a list of available ingredients (from a user's fridge, pantry, or kitchen) and must generate between 3 and 5 distinct, practical recipes the user can cook with those ingredients.

${allergyClause}
${prefClause}
```
With:
```js
function buildRecipeSystemPrompt(allergies = [], preferences = [], dislikes = []) {
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
      ? `The user dislikes: ${dislikes.join(', ')}. Avoid using these as main ingredients in recipes where possible. You may list them under "additional_ingredients_optional" only if there is no reasonable substitute.`
      : '';

  return `You are an expert chef and nutritionist AI.
You will receive a list of available ingredients (from a user's fridge, pantry, or kitchen) and must generate between 3 and 5 distinct, practical recipes the user can cook with those ingredients.

${allergyClause}
${prefClause}
${dislikesClause}
```

- [ ] **Step 2: Update `generateRecipesFromIngredients` to pass `dislikes` to the prompt**

Replace:
```js
  const { allergies = [], preferences = [] } = userPreferences;
```
With:
```js
  const { allergies = [], preferences = [], dislikes = [] } = userPreferences;
```

Then replace the `buildRecipeSystemPrompt` call inside the loop:
```js
      const systemPrompt =
        schemaFailures > 0
          ? buildRecipeSystemPrompt(allergies, preferences) + SCHEMA_RETRY_SUFFIX
          : buildRecipeSystemPrompt(allergies, preferences);
```
With:
```js
      const systemPrompt =
        schemaFailures > 0
          ? buildRecipeSystemPrompt(allergies, preferences, dislikes) + SCHEMA_RETRY_SUFFIX
          : buildRecipeSystemPrompt(allergies, preferences, dislikes);
```

- [ ] **Step 3: Update `buildIngredientSystemPrompt` to accept and ignore dislikes (keeps signature consistent)**

Replace:
```js
function buildIngredientSystemPrompt(allergies = [], preferences = []) {
  const dietaryContext = allergies.length > 0 || preferences.length > 0
```
With:
```js
function buildIngredientSystemPrompt(allergies = [], preferences = [], dislikes = []) {
  // dislikes are intentionally ignored here — disliked items are still detected
  // (they're in the fridge) and only avoided during recipe generation
  const dietaryContext = allergies.length > 0 || preferences.length > 0
```

- [ ] **Step 4: Update `detectIngredientsFromImage` call to pass dislikes**

Replace:
```js
  const { allergies = [], preferences = [] } = userPreferences;
```
(inside `detectIngredientsFromImage`) with:
```js
  const { allergies = [], preferences = [], dislikes = [] } = userPreferences;
```

Then replace:
```js
            content: buildIngredientSystemPrompt(allergies, preferences),
```
With:
```js
            content: buildIngredientSystemPrompt(allergies, preferences, dislikes),
```

- [ ] **Step 5: Verify the AI service loads cleanly**

```bash
node -e "require('./src/services/aiService'); console.log('OK')"
```
Expected: `OK`

---

## Task 7: Update Python test helpers

**Files:**
- Modify: `tests/utils/api_client.py`
- Modify: `tests/utils/validators.py`

- [ ] **Step 1: Add `dislikes` param to `set_user_preferences` in `api_client.py`**

Replace:
```python
    def set_user_preferences(self, user_id, allergies=None, preferences=None):
        """PUT /api/users/:userId/preferences"""
        payload = {
            'allergies':   allergies   or [],
            'preferences': preferences or [],
        }
```
With:
```python
    def set_user_preferences(self, user_id, allergies=None, preferences=None, dislikes=None):
        """PUT /api/users/:userId/preferences"""
        payload = {
            'allergies':   allergies   or [],
            'preferences': preferences or [],
            'dislikes':    dislikes    or [],
        }
```

- [ ] **Step 2: Add `dislikes` to `assert_user_preferences_response` in `validators.py`**

Replace:
```python
def assert_user_preferences_response(data, user_id=None):
    """Validate the user preferences response schema."""
    assert data.get('success') is True, \
        f"Expected success=true, got {data.get('success')}. Data: {data}"
    assert 'user_id'     in data, f"Missing 'user_id': {data}"
    assert 'allergies'   in data, f"Missing 'allergies': {data}"
    assert 'preferences' in data, f"Missing 'preferences': {data}"
    assert isinstance(data['allergies'],   list), "'allergies' must be a list"
    assert isinstance(data['preferences'], list), "'preferences' must be a list"
    if user_id is not None:
        assert data['user_id'] == user_id, \
            f"Expected user_id='{user_id}', got '{data['user_id']}'"
```
With:
```python
def assert_user_preferences_response(data, user_id=None):
    """Validate the user preferences response schema."""
    assert data.get('success') is True, \
        f"Expected success=true, got {data.get('success')}. Data: {data}"
    assert 'user_id'     in data, f"Missing 'user_id': {data}"
    assert 'allergies'   in data, f"Missing 'allergies': {data}"
    assert 'preferences' in data, f"Missing 'preferences': {data}"
    assert 'dislikes'    in data, f"Missing 'dislikes': {data}"
    assert isinstance(data['allergies'],   list), "'allergies' must be a list"
    assert isinstance(data['preferences'], list), "'preferences' must be a list"
    assert isinstance(data['dislikes'],    list), "'dislikes' must be a list"
    if user_id is not None:
        assert data['user_id'] == user_id, \
            f"Expected user_id='{user_id}', got '{data['user_id']}'"
```

---

## Task 8: Add dislikes tests to `test_user_preferences.py`

**Files:**
- Modify: `tests/unit/test_user_preferences.py`

- [ ] **Step 1: Add dislikes tests after `test_put_values_normalised_to_lowercase`**

Insert these test functions before the DELETE section:

```python
# ═══════════════════════════════════════════════════════════════════════════════
#  DISLIKES -- soft avoid field
# ═══════════════════════════════════════════════════════════════════════════════

def test_put_dislikes_saved_and_returned():
    """PUT with dislikes -> 200, dislikes reflected in response."""
    uid = _unique_user()
    r = client.set_user_preferences(uid, allergies=[], preferences=[], dislikes=['mushrooms', 'olives'])
    assert r.status_code == 200, f'Expected 200, got {r.status_code}: {r.text[:300]}'
    data = r.json()
    assert_user_preferences_response(data, user_id=uid)
    assert 'mushrooms' in data['dislikes'], f'mushrooms not saved: {data["dislikes"]}'
    assert 'olives'    in data['dislikes'], f'olives not saved: {data["dislikes"]}'


def test_get_returns_dislikes():
    """GET after PUT reflects dislikes."""
    uid = _unique_user()
    client.set_user_preferences(uid, allergies=[], preferences=[], dislikes=['coriander'])
    r = client.get_user_preferences(uid)
    assert r.status_code == 200
    data = r.json()
    assert_user_preferences_response(data, user_id=uid)
    assert 'coriander' in data['dislikes'], f'coriander missing from GET: {data["dislikes"]}'


def test_put_dislikes_normalised_to_lowercase():
    """Dislikes are normalised to lowercase on save."""
    uid = _unique_user()
    r = client.set_user_preferences(uid, allergies=[], preferences=[], dislikes=['MUSHROOMS', 'Olives'])
    assert r.status_code == 200
    data = r.json()
    for d in data['dislikes']:
        assert d == d.lower(), f'dislike not lowercased: {d}'


def test_put_dislikes_empty_array_accepted():
    """PUT with empty dislikes array -> 200."""
    uid = _unique_user()
    r = client.set_user_preferences(uid, allergies=['nuts'], preferences=[], dislikes=[])
    assert r.status_code == 200
    data = r.json()
    assert data['dislikes'] == [], f'Expected empty dislikes, got: {data["dislikes"]}'


def test_put_dislike_string_too_long_returns_400():
    """Dislike string > 50 chars -> 400 INVALID_PREFERENCES."""
    uid = _unique_user()
    r = client.set_user_preferences(uid, allergies=[], preferences=[], dislikes=['x' * 51])
    assert r.status_code == 400, f'Expected 400, got {r.status_code}: {r.text[:300]}'
    assert_error_envelope(r, expected_status=400)


def test_get_unknown_user_dislikes_empty():
    """GET for unknown user -> dislikes is empty array."""
    r = client.get_user_preferences(f'unknown_{uuid.uuid4().hex}')
    assert r.status_code == 200
    data = r.json()
    assert data['dislikes'] == [], f'Expected empty dislikes for unknown user: {data}'
```

- [ ] **Step 2: Register the new tests in the `run()` function**

Add these lines inside `run()` after `runner.run('PUT values normalised to lowercase', ...)`:

```python
    runner.run('PUT dislikes saved and returned -> 200',         test_put_dislikes_saved_and_returned)
    runner.run('GET returns dislikes after PUT',                  test_get_returns_dislikes)
    runner.run('PUT dislikes normalised to lowercase',           test_put_dislikes_normalised_to_lowercase)
    runner.run('PUT empty dislikes array -> 200',                test_put_dislikes_empty_array_accepted)
    runner.run('PUT dislike string > 50 chars -> 400',           test_put_dislike_string_too_long_returns_400)
    runner.run('GET unknown user dislikes -> empty array',       test_get_unknown_user_dislikes_empty)
```

- [ ] **Step 3: Run the tests (skip-ai — no OpenAI needed)**

```bash
python tests/run_all_tests.py --unit --skip-ai
```
Expected: All previously passing tests still pass + 6 new dislikes tests pass. Zero failures.

---

## Task 9: Update Postman collection

**Files:**
- Modify: `tests/AI_Recipe_API.postman_collection.json`

- [ ] **Step 1: Update "Save Preferences" request body**

Find the request named `"PUT — Save Preferences"` and replace its `raw` body:
```json
"raw": "{\n  \"allergies\": [\"nuts\", \"dairy\"],\n  \"preferences\": [\"vegetarian\", \"halal\"]\n}"
```
With:
```json
"raw": "{\n  \"allergies\": [\"nuts\", \"dairy\"],\n  \"preferences\": [\"vegetarian\", \"halal\"],\n  \"dislikes\": [\"mushrooms\", \"olives\"]\n}"
```

- [ ] **Step 2: Update "Detect — Image URL + Allergy Filter" request body to include dislikes**

Find the request named `"Detect — Image URL + Allergy Filter (NEW)"` and replace its `raw` body:
```json
"raw": "{\n  \"image_url\": \"https://images.unsplash.com/photo-1540420773420-3366772f4999?w=600&q=80\",\n  \"user_preferences\": {\n    \"allergies\": [\"dairy\", \"gluten\"],\n    \"preferences\": [\"vegetarian\"]\n  }\n}"
```
With:
```json
"raw": "{\n  \"image_url\": \"https://images.unsplash.com/photo-1540420773420-3366772f4999?w=600&q=80\",\n  \"user_preferences\": {\n    \"allergies\": [\"dairy\", \"gluten\"],\n    \"preferences\": [\"vegetarian\"],\n    \"dislikes\": [\"mushrooms\", \"olives\"]\n  }\n}"
```

- [ ] **Step 3: Update "Generate Recipes — With Preferences" body to include dislikes**

Find the request named `"Generate Recipes — With Preferences"` and replace its `raw` body:
```json
"raw": "{\n  \"ingredients\": [\"eggs\", \"flour\", \"butter\", \"milk\", \"sugar\", \"vanilla\"],\n  \"user_preferences\": {\n    \"allergies\": [\"nuts\"],\n    \"preferences\": [\"vegetarian\"]\n  }\n}"
```
With:
```json
"raw": "{\n  \"ingredients\": [\"eggs\", \"flour\", \"butter\", \"milk\", \"sugar\", \"vanilla\"],\n  \"user_preferences\": {\n    \"allergies\": [\"nuts\"],\n    \"preferences\": [\"vegetarian\"],\n    \"dislikes\": [\"cinnamon\"]\n  }\n}"
```
