import fsPromises from "fs/promises";
import sharp from "sharp";
import OpenAI from "openai";
import { ApiError } from "../utils/ApiError.js";

/**
 * Scan pipelines, built for speed.
 *
 * Photo scan:  1. vision pass (small output): ingredients + 3 recipe ideas
 *              2. the 3 recipes written IN PARALLEL
 * Typed scan:  1. text pass (small output): 3 recipe ideas
 *              2. the 3 recipes written IN PARALLEL
 *
 * Output tokens dominate latency: writing 3 recipes in one answer is ~3x
 * slower than writing them side by side. Every call uses a strict JSON
 * schema, so answers are never cut or malformed.
 *
 * Benchmark (2026-11-16, same photo): gpt-5.4-mini without reasoning reads a
 * photo in ~3 s (gpt-4o-mini ~7 s, and bills ~25k input tokens per image) and
 * writes a recipe at ~140 tok/s (gpt-4o-mini ~50-70 tok/s).
 */

const SCAN_MODEL = process.env.OPENAI_SCAN_MODEL || "gpt-5.4-mini";
// Second look when the first scan finds nothing (also covers the scan model failing).
const VISION_FALLBACK_MODEL = process.env.OPENAI_VISION_MODEL || "gpt-4o";
const RECIPE_MODEL = process.env.OPENAI_RECIPE_MODEL || "gpt-5.4-mini";
const RECIPE_COUNT = 3;
const MAX_IMAGE_SIDE = 1024;
const JPEG_QUALITY = 80;
// A recipe usually takes 4-8 s: a call still running after 20 s is stuck, retry it.
const RECIPE_CALL_OPTIONS = { timeout: 20_000, maxRetries: 1 };
// Once the first recipe is ready, the others get this long; laggards are dropped
// so one slow call can never hold the whole scan.
const STRAGGLER_GRACE_MS = 4_000;

let cachedClient = null;
const getClient = () => {
    if (cachedClient) return cachedClient;
    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) {
        throw new ApiError(500, "Missing OPENAI_API_KEY in environment variables");
    }
    // The Spring backend waits 120 s in total: fail fast and retry once
    // rather than hanging on a stuck request.
    cachedClient = new OpenAI({ apiKey, timeout: 45_000, maxRetries: 1 });
    return cachedClient;
};

/** GPT-5 / o-series take a reasoning effort (none = fastest) and no temperature. */
const modelParams = (model, temperature) =>
    /^(gpt-5|o\d)/.test(model) ? { reasoning: { effort: "none" } } : { temperature };

// ---------------------------------------------------------------- images

/** Resize (longest side 1024), fix orientation, re-encode as JPEG. */
const toImageDataUrl = async (file) => {
    try {
        const input = file.buffer ?? (await fsPromises.readFile(file.path));
        const jpeg = await sharp(input, { failOn: "none" })
            .rotate()
            .resize({ width: MAX_IMAGE_SIDE, height: MAX_IMAGE_SIDE, fit: "inside", withoutEnlargement: true })
            .flatten({ background: "#ffffff" })
            .jpeg({ quality: JPEG_QUALITY, mozjpeg: true })
            .toBuffer();
        return `data:image/jpeg;base64,${jpeg.toString("base64")}`;
    } catch (error) {
        throw new ApiError(400, `Unable to process image "${file.originalname}". ${error.message}`);
    }
};

// --------------------------------------------------------------- schemas

const strictObject = (properties) => ({
    type: "object",
    additionalProperties: false,
    properties,
    required: Object.keys(properties),
});
const str = { type: "string" };
const int = { type: "integer" };
const strList = { type: "array", items: str };

const IDEAS = {
    type: "array",
    items: strictObject({
        title: str,
        cuisine: str,
        meal_type: str,
        description: str,
        key_ingredients: strList,
    }),
};

// Names only: every extra field per ingredient is written ~25 times and output
// tokens are the scan's latency (23 items: 7.9 s with 5 fields, 2.3 s names only).
// The allergen field exists only when the user declared allergies.
const INGREDIENT_LIST = (withAllergens) => ({
    type: "array",
    items: withAllergens ? strictObject({ name: str, allergen_reason: str }) : str,
});
const detectionSchema = (withAllergens, task) =>
    strictObject({
        ...(task !== "ideas" ? { ingredients: INGREDIENT_LIST(withAllergens) } : {}),
        ...(task !== "ingredients" ? { recipe_ideas: IDEAS } : {}),
    });

const IDEAS_SCHEMA = strictObject({ feasible: { type: "boolean" }, recipe_ideas: IDEAS });

const RECIPE_SCHEMA = strictObject({
    title: str,
    description: str,
    cuisine: str,
    meal_type: str,
    prep_time_minutes: int,
    cook_time_minutes: int,
    servings: int,
    kcal_per_serving: int,
    ingredients: { type: "array", items: strictObject({ name: str, quantity: str }) },
    steps: strList,
    equipment: strList,
    tips: str,
});

const jsonSchemaFormat = (name, schema) => ({
    format: { type: "json_schema", name, schema, strict: true },
});

// --------------------------------------------------------------- prompts

const STAPLES = "salt, pepper, cooking oil and water";

const listOrNone = (value) => {
    if (Array.isArray(value)) return value.filter(Boolean).join(", ") || "none";
    return value ? String(value) : "none";
};

/** "FR Français" / "fr" / "French" -> "French"; English when unknown. */
const languageName = (raw) => {
    const v = String(raw || "").trim().toLowerCase();
    if (v.startsWith("fr")) return "French";
    if (v.startsWith("es")) return "Spanish";
    return "English";
};

const buildProfile = (prefs = {}) => `USER PROFILE
- Allergies (never use these): ${listOrNone(prefs.allergies)}
- Dietary preferences: ${listOrNone(prefs.preferences)}
- Favourite cuisines: ${listOrNone(prefs.cuisines_love)}
- Kitchen tools: ${listOrNone(prefs.kitchen_tools)}
- Cooking skill: ${listOrNone(prefs.skill_level)}
- Budget: ${listOrNone(prefs.budget)}
- Goals: ${listOrNone(prefs.goals)}`;

/** Instructions the Spring backend adds (e.g. the strict typed-scan rule). */
const appInstructions = (prefs = {}) =>
    typeof prefs.system_instructions === "string" && prefs.system_instructions.trim()
        ? `\nAPP INSTRUCTIONS (follow strictly):\n${prefs.system_instructions.trim()}\n`
        : "";

const IDEA_RULES = (staples) => `- Propose exactly ${RECIPE_COUNT} clearly different dishes (different cooking method and style).
- ${staples ? `Assume only ${STAPLES} are also available.` : "Use ONLY the listed ingredients: no other ingredient, not even pantry staples."}
- If no realistic dish can be made (e.g. only a condiment, a drink or a single seasoning), return an empty list. Never invent a dish just to fill the list.
- Favour the user's cuisines, diet, tools and skill level; never use an allergen.
- description: one appetising sentence. key_ingredients: the listed items the dish relies on.`;

const INGREDIENTS_TASK = (withAllergens) => `INGREDIENTS
- List every visible food or drink: every shelf, door, drawer, bowl, jar, bottle, bag and package. Read labels and brands.
- Name only what the image supports. Never identify an item from colour or shape alone (green onions are not lettuce).
- Use the plain grocery name of each item, 1 to 3 words, without brand, quantity or notes ("cherry tomatoes", "feta cheese").
- A flavoured product is one product (strawberry yogurt = yogurt). Each item once.
- If unsure, use a broader honest name ("cheese", "sauce", "vegetables") instead of leaving it out.
${withAllergens ? `- allergen_reason: if the item contains one of the allergies listed in the USER PROFILE, explain briefly (e.g. "contains peanuts"); otherwise "".\n` : ""}- Return an empty list ONLY when there is no food at all.`;

const IDEAS_TASK = `RECIPE IDEAS built from the foods visible in the photo
${IDEA_RULES(true)}
- Never use an item that contains one of the user's allergies (title, description or key_ingredients).
- key_ingredients: plain grocery names, 1 to 3 words each.`;

/** task: "ingredients", "ideas" or "both" (one call doing both, used for the second look). */
const detectionPrompt = (prefs, language, withAllergens, task) => `You are a food vision expert. Look at the photo(s) of a fridge, pantry, table or groceries.

${[task !== "ideas" ? INGREDIENTS_TASK(withAllergens) : "", task !== "ingredients" ? IDEAS_TASK : ""].filter(Boolean).join("\n\n")}

${buildProfile(prefs)}
${task === "ingredients" ? "" : appInstructions(prefs)}
Write every name, title and description in ${language}.`;

const SECOND_LOOK_TEXT = `
SECOND LOOK: a first analysis found no ingredients, which is almost always wrong.
Examine every area again, including partly hidden, blurry, dark or packaged items, and list every food or drink you can see.`;

const ideasPrompt = (available, prefs, language, staples = false) => `You are a creative chef.

INGREDIENTS: ${available.join(", ")}

FEASIBILITY
- feasible = false when these ingredients alone cannot make a real dish people eat as a meal, snack or side
  (e.g. only condiments, sauces, drinks or seasonings such as ketchup, mustard, soda, salt). Then return no ideas.

RECIPE IDEAS
${IDEA_RULES(staples)}

${buildProfile(prefs)}
${appInstructions(prefs)}
Write titles and descriptions in ${language}.`;

const recipePrompt = ({ idea, otherTitles, available, forbidden, staples, prefs, language }) => `You are a professional chef writing a complete, reliable home recipe.

DISH IDEA: ${idea.title} (${idea.cuisine}, ${idea.meal_type})
IDEA: ${idea.description}
Must clearly differ from: ${otherTitles.join("; ") || "-"}

AVAILABLE INGREDIENTS (use only these${staples ? `, plus ${STAPLES}` : ", nothing else"}):
${available.join(", ")}

FORBIDDEN (allergens, never use, never mention in the title): ${forbidden.join(", ") || "none"}
If the idea relies on a forbidden or unavailable item, adapt the dish and rename it accordingly.

${buildProfile(prefs)}
${appInstructions(prefs)}
RULES
- List every ingredient the recipe uses with a precise quantity and unit; never hide one in the steps.
- 6 to 10 numbered steps ("1. ..."), each concrete: preparation, heat level / oven temperature, timings, doneness checks, assembly and serving.
- Respect allergies and dietary preferences strictly.
- Realistic times in minutes, servings as an integer, kcal_per_serving as an honest estimate.
- tips: 2 short, precise sentences (texture or a flavour variation, and storage).
- Write every text value in ${language}.`;

// ----------------------------------------------------------------- calls

const parseJson = (response) => {
    if (response?.status === "incomplete") {
        throw new ApiError(502, `OpenAI answer incomplete (${response.incomplete_details?.reason || "unknown"})`);
    }
    const text = (response?.output_text || "").trim();
    if (!text) throw new ApiError(502, "OpenAI returned an empty response");
    return JSON.parse(text);
};

/** Vision pass. Ingredients come back as { ingredient_name, allergen_reason }. */
const detect = async (client, model, images, prefs, language, withAllergens, task, extraText = "") => {
    const response = await client.responses.create({
        model,
        input: [
            {
                role: "user",
                content: [
                    { type: "input_text", text: detectionPrompt(prefs, language, withAllergens, task) + extraText },
                    ...images.map((url) => ({ type: "input_image", image_url: url, detail: "high" })),
                ],
            },
        ],
        ...modelParams(model, 0.2),
        max_output_tokens: 1500,
        text: jsonSchemaFormat("scan_detection", detectionSchema(withAllergens, task)),
    });
    const parsed = parseJson(response);
    const seen = new Set();
    parsed.ingredients = (parsed.ingredients || [])
        .map((i) => (typeof i === "string" ? { name: i, allergen_reason: "" } : i))
        .map((i) => ({ ingredient_name: String(i.name || "").trim(), allergen_reason: i.allergen_reason || "" }))
        .filter((i) => i.ingredient_name && !seen.has(i.ingredient_name.toLowerCase()) && seen.add(i.ingredient_name.toLowerCase()));
    return parsed;
};

const proposeIdeas = async (client, available, prefs, language, staples = false) => {
    const response = await client.responses.create({
        model: RECIPE_MODEL,
        input: ideasPrompt(available, prefs, language, staples),
        ...modelParams(RECIPE_MODEL, 0.8),
        max_output_tokens: 800,
        text: jsonSchemaFormat("recipe_ideas", IDEAS_SCHEMA),
    });
    const parsed = parseJson(response);
    return parsed.feasible === false ? [] : parsed.recipe_ideas || [];
};

const proposeIdeasWithStaples = (client, available, prefs, language) =>
    proposeIdeas(client, available, prefs, language, true).catch((error) => {
        console.warn(`[scan] fallback ideas failed: ${error?.message}`);
        return [];
    });

const writeRecipe = async (client, args) => {
    const response = await client.responses.create({
        model: RECIPE_MODEL,
        input: recipePrompt(args),
        ...modelParams(RECIPE_MODEL, 0.7),
        max_output_tokens: 2500,
        text: jsonSchemaFormat("recipe", RECIPE_SCHEMA),
    }, RECIPE_CALL_OPTIONS);
    const r = parseJson(response);
    // Field names understood by normalizeRecipeForCooked.
    return {
        title: r.title,
        description: r.description,
        cuisine: r.cuisine,
        meal_type: r.meal_type,
        prep_time: r.prep_time_minutes,
        cook_time: r.cook_time_minutes,
        servings: r.servings,
        kcal: r.kcal_per_serving,
        ingredients: r.ingredients,
        steps: r.steps,
        equipment: r.equipment,
        tips: r.tips,
    };
};

/**
 * Writes every idea as a full recipe, in parallel. Failed recipes are dropped,
 * and so are the ones still running {@link STRAGGLER_GRACE_MS} after the first
 * one is ready. Recipes keep the ideas' order.
 */
const writeRecipes = async (client, ideas, shared) => {
    const results = new Array(ideas.length).fill(null);
    let firstDone;
    const firstReady = new Promise((resolve) => (firstDone = resolve));
    const tasks = ideas.map((idea, index) =>
        writeRecipe(client, {
            ...shared,
            idea,
            otherTitles: ideas.filter((o) => o !== idea).map((o) => o.title),
        }).then(
            (recipe) => {
                results[index] = recipe;
                firstDone();
            },
            (error) => console.warn(`[scan] recipe "${idea.title}" failed: ${error?.message}`)
        )
    );
    const all = Promise.all(tasks);
    let timer;
    const grace = firstReady.then(() => new Promise((resolve) => (timer = setTimeout(resolve, STRAGGLER_GRACE_MS))));
    await Promise.race([all, grace]);
    clearTimeout(timer);
    const dropped = results.filter((r) => r === null).length;
    if (dropped && results.some(Boolean)) console.warn(`[scan] ${dropped} recipe(s) dropped (failed or too slow)`);
    return results.filter(Boolean);
};

const hasIngredients = (parsed) => Array.isArray(parsed?.ingredients) && parsed.ingredients.length > 0;

// ------------------------------------------------------------- pipelines

/**
 * Photo scan.
 * @returns {{ ingredients: object[], restricted: object[], recipes: object[] }}
 * Throws ApiError 422 when no food is visible.
 */
export const scanFridge = async (files, prefs = {}) => {
    const client = getClient();
    const language = languageName(prefs.language);
    const hasAllergies = listOrNone(prefs.allergies) !== "none";
    const started = Date.now();

    const images = await Promise.all(files.map(toImageDataUrl));

    // The ingredient list and the recipe ideas are read from the photo side by side
    // (two short answers in parallel instead of one long one).
    const [listed, proposed] = await Promise.allSettled([
        detect(client, SCAN_MODEL, images, prefs, language, hasAllergies, "ingredients"),
        detect(client, SCAN_MODEL, images, prefs, language, hasAllergies, "ideas"),
    ]);
    if (listed.status === "rejected") console.warn(`[scan] detection failed (${SCAN_MODEL}): ${listed.reason?.message}`);
    if (proposed.status === "rejected") console.warn(`[scan] ideas failed (${SCAN_MODEL}): ${proposed.reason?.message}`);
    let detection = listed.status === "fulfilled" ? listed.value : null;
    if (detection) detection.recipe_ideas = proposed.status === "fulfilled" ? proposed.value.recipe_ideas || [] : [];
    // Nothing found (dark fridge, cluttered shelf, odd angle) or failed call:
    // a second, more careful look with another model before giving up.
    if (!hasIngredients(detection)) {
        console.warn(`[scan] no ingredients on first pass, retrying with ${VISION_FALLBACK_MODEL}`);
        try {
            detection = await detect(client, VISION_FALLBACK_MODEL, images, prefs, language, hasAllergies, "both", SECOND_LOOK_TEXT);
        } catch (error) {
            throw new ApiError(502, error?.message || "Failed to scan image");
        }
    }
    const detectedAt = Date.now();

    const all = Array.isArray(detection?.ingredients) ? detection.ingredients : [];
    // Only trust allergen flags when the user actually declared allergies.
    const isRestricted = (i) => hasAllergies && Boolean(i.allergen_reason?.trim());
    const restricted = all.filter(isRestricted);
    const ingredients = all.filter((i) => !isRestricted(i));
    if (!ingredients.length) {
        throw new ApiError(422, "No ingredients were detected in the uploaded image(s).");
    }

    let ideas = (detection.recipe_ideas || []).slice(0, RECIPE_COUNT);
    if (!ideas.length) {
        // The ideas call failed or came back empty: ask again from the ingredient names.
        ideas = (await proposeIdeasWithStaples(client, ingredients.map((i) => i.ingredient_name), prefs, language)).slice(0, RECIPE_COUNT);
    }
    if (!ideas.length) {
        throw new ApiError(502, "No recipes were generated from the detected ingredients.");
    }
    // Items an idea relies on that the list missed (both calls read the same photo).
    const available = [...new Set([
        ...ingredients.map((i) => i.ingredient_name),
        ...ideas.flatMap((idea) => idea.key_ingredients || []),
    ].map((n) => String(n).trim()).filter(Boolean))];
    const forbidden = restricted.map((i) => i.ingredient_name);
    const allowedAvailable = available.filter((n) => !forbidden.some((f) => f.toLowerCase() === n.toLowerCase()));

    const recipes = await writeRecipes(client, ideas, {
        available: allowedAvailable,
        forbidden,
        staples: true,
        prefs,
        language,
    });
    if (!recipes.length) {
        throw new ApiError(502, "No recipes were generated from the detected ingredients.");
    }

    console.log(
        `[scan] ${files.length} image(s), ${ingredients.length} ingredient(s), ${restricted.length} restricted, ` +
            `${recipes.length} recipe(s) - detection ${detectedAt - started} ms, recipes ${Date.now() - detectedAt} ms`
    );
    return { ingredients, restricted, recipes };
};

/**
 * Typed scan / generation from a list of ingredient names. Returns raw
 * recipes (possibly empty when nothing realistic can be cooked).
 */
export const recipesFromIngredients = async (ingredients, prefs = {}) => {
    const available = (Array.isArray(ingredients) ? ingredients : [ingredients])
        .map((i) => (typeof i === "string" ? i : i?.name || ""))
        .map((s) => s.trim())
        .filter(Boolean);
    if (!available.length) return [];

    const client = getClient();
    const language = languageName(prefs.language);
    const started = Date.now();

    const ideas = (await proposeIdeas(client, available, prefs, language)).slice(0, RECIPE_COUNT);
    const ideasAt = Date.now();
    if (!ideas.length) return [];

    const recipes = await writeRecipes(client, ideas, { available, forbidden: [], staples: false, prefs, language });
    console.log(
        `[generate] ${available.length} ingredient(s), ${recipes.length} recipe(s) - ` +
            `ideas ${ideasAt - started} ms, recipes ${Date.now() - ideasAt} ms`
    );
    return recipes;
};
