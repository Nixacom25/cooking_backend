import fsPromises from "fs/promises";
import path from "path";
import sharp from "sharp";
import OpenAI from "openai";
import { ApiError } from "../utils/ApiError.js";
import { imageIngredientsPrompt } from "../prompts/ingredientsScanningPrompt.js";
import { scanAndRecipePrompt } from "../prompts/scanAndRecipePrompt.js";
import {createRecipePrompt} from "../prompts/diggerentRecipiePrompt.js"

const DEFAULT_OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-4o-mini";
const PROMPT_TEMPLATE_PATH = path.resolve(process.cwd(), "prompt.md");
const OPENAI_SUPPORTED_IMAGE_TYPES = new Set([
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
]);
const MAX_WIDTH = 512;
const JPEG_QUALITY = 65;
const PNG_QUALITY = 75;
let cachedClient = null;

const singlePassScanAndRecipePrompt = `
You are RecipeAI Pro — High-Speed Food Vision Engine.

Analyze image(s) and return valid JSON with detected ingredients and 3 fast recipes.

JSON Structure:
{
  "status": "success",
  "ingredients": [
    { "ingredient_name": "Chicken", "category": "Meat", "estimated_quantity": "4 pcs", "confidence": 0.98 }
  ],
  "recipes": [
    {
      "title": "Crispy Chicken",
      "description": "Golden fried chicken.",
      "cuisine": "American",
      "prep_time": "10 min",
      "cook_time": "15 min",
      "total_time": "25 min",
      "servings": "2",
      "ingredients": [ { "name": "Chicken", "quantity": "4 pcs" } ],
      "instructions": [ "1. Season chicken.", "2. Fry 15 min until golden and serve." ],
      "nutrition": { "calories": "450 kcal", "protein": "30g", "carbs": "5g", "fat": "25g" }
    }
  ],
  "fallback_message": ""
}

RULES:
- Identify ALL visible ingredients.
- Generate 3 distinct recipes.
- Keep descriptions and instructions (max 2 short steps) extremely concise.
- Return ONLY valid JSON.
`;
let cachedPromptTemplate = null;

const getOpenAIClient = async () => {
    if (cachedClient) {
        return cachedClient;
    }

    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) {
        throw new ApiError(500, "Missing OPENAI_API_KEY in environment variables");
    }

    cachedClient = new OpenAI({ apiKey });
    return cachedClient;
};

const getPromptTemplate = async () => {
    if (cachedPromptTemplate) {
        return cachedPromptTemplate;
    }

    try {
        cachedPromptTemplate = await fsPromises.readFile(PROMPT_TEMPLATE_PATH, "utf8");
    } catch {
        throw new ApiError(500, "Unable to load prompt.md template for OpenAI service");
    }

    return cachedPromptTemplate;
};

const buildPrompt = (template, captionText) => {
    return template.replace("{{INSERT_CAPTION_HERE}}", captionText.trim());
};

const extractTextFromResponse = (response) => {
    if (typeof response?.output_text === "string" && response.output_text.trim()) {
        return response.output_text.trim();
    }

    const text =
        response?.output
            ?.flatMap((item) => item?.content || [])
            ?.find((content) => content?.type === "output_text")?.text ||
        "";

    return text.trim();
};

const parseModelJson = (rawText) => {
    const cleaned = rawText
        .replace(/^```json\s*/i, "")
        .replace(/^```\s*/i, "")
        .replace(/\s*```$/, "")
        .trim();

    try {
        return JSON.parse(cleaned);
    } catch {
        try {
            const firstBrace = cleaned.indexOf("{");
            if (firstBrace !== -1) {
                let jsonStr = cleaned.slice(firstBrace);
                // Attempt basic bracket completion
                if (!jsonStr.endsWith("}")) {
                    jsonStr = jsonStr.replace(/,[^,]*$/, "") + '}]}';
                }
                return JSON.parse(jsonStr);
            }
        } catch (_) {}
        throw new ApiError(502, "OpenAI returned invalid JSON");
    }
};

const normalizeRecipeResponse = (payload) => {
    if (!payload || typeof payload !== "object") {
        throw new ApiError(502, "OpenAI returned an empty payload");
    }

    return payload;
};

// const toOpenAiImageDataUrl = async (file) => {
//     let buffer = await fsPromises.readFile(file.path);
//     let mimeType = file.mimetype;

//     if (!OPENAI_SUPPORTED_IMAGE_TYPES.has(mimeType)) {
//         try {
//             buffer = await sharp(buffer).jpeg({ quality: 85 }).toBuffer();
//             mimeType = "image/jpeg";
//         } catch {
//             throw new ApiError(
//                 400,
//                 `Unsupported image format: ${file.originalname}. Use JPEG, PNG, GIF, WebP, or AVIF.`
//             );
//         }
//     }

//     return `data:${mimeType};base64,${buffer.toString("base64")}`;
// };


const toOpenAiImageDataUrl = async (file) => {
    try {
        let buffer = file.buffer ? file.buffer : await fsPromises.readFile(file.path);

        const metadata = await sharp(buffer).metadata();

        let optimizedBuffer;
        let mimeType;

        // Images with transparency
        if (metadata.hasAlpha) {
            optimizedBuffer = await sharp(buffer)
                .resize({
                    width: MAX_WIDTH,
                    withoutEnlargement: true,
                    fit: "inside",
                })
                .png({
                    quality: PNG_QUALITY,
                    compressionLevel: 4,
                })
                .toBuffer();

            mimeType = "image/png";
        } else {
        // Normal photos
        optimizedBuffer = await sharp(buffer)
            .resize({
                width: MAX_WIDTH,
                withoutEnlargement: true,
                fit: "inside",
            })
            .jpeg({
                quality: JPEG_QUALITY,
            })
            .toBuffer();

        mimeType = "image/jpeg";
    }

    return `data:${mimeType};base64,${optimizedBuffer.toString("base64")}`;
    } catch (error) {
        throw new ApiError(
            400,
            `Unable to process image "${file.originalname}". ${error.message}`
        );
    }
};


const extractRecipeFromCaption = async (captionText) => {
    if (!captionText || typeof captionText !== "string") {
        throw new ApiError(400, "captionText must be a non-empty string");
    }

    const client = await getOpenAIClient();
    const trimmedCaption = captionText.slice(0, 800);

    const systemPrompt = `You are RecipeAI UltraFast. Extract recipe details into compact JSON in under 2 seconds. Concise steps (max 3-4 steps).`;
    const userPrompt = `Caption:
${trimmedCaption}

Output JSON format:
{
  "status": "success",
  "recipe": {
    "title": "Title",
    "description": "Short description",
    "prep_time": "10 mins",
    "cook_time": "15 mins",
    "servings": "2",
    "cuisine": "Cuisine",
    "ingredients": [
      { "name": "Ingredient name", "quantity": "1 piece" }
    ],
    "instructions": [
      "1. Step instruction 1",
      "2. Step instruction 2"
    ],
    "metadata": {
      "cuisine": "Cuisine",
      "meal_type": "Dinner"
    }
  },
  "fallback_message": ""
}
Rules: Extract ingredients and concise steps. Return ONLY valid JSON.`;

    try {
        let rawText = "";
        // Use fast chat completions if available
        if (client.chat && client.chat.completions) {
            const completion = await client.chat.completions.create({
                model: DEFAULT_OPENAI_MODEL,
                messages: [
                    { role: "system", content: systemPrompt },
                    { role: "user", content: userPrompt }
                ],
                response_format: { type: "json_object" },
                max_tokens: 450,
                temperature: 0.2
            });
            rawText = completion.choices[0]?.message?.content || "";
        } else {
            const response = await client.responses.create({
                model: DEFAULT_OPENAI_MODEL,
                input: [
                    {
                        role: "user",
                        content: [{ type: "input_text", text: `${systemPrompt}\n\n${userPrompt}` }],
                    },
                ],
                max_output_tokens: 450,
                text: { format: { type: "json_object" } },
            });
            rawText = extractTextFromResponse(response);
        }

        if (!rawText) {
            throw new ApiError(502, "OpenAI returned an empty response");
        }

        const parsed = JSON.parse(rawText);
        return normalizeRecipeResponse(parsed);
    } catch (error) {
        throw new ApiError(502, error?.message || "Failed to extract recipe from caption");
    }
};

const scanWithAI = async (files) => {
    const client = await getOpenAIClient();

    const imageContent = await Promise.all(
        files.map(async (file) => ({
            type: "input_image",
            image_url: await toOpenAiImageDataUrl(file),
             detail: "low",
        }))
    );

    const input = [
        {
            role: "user",
            content: [
                {
                    type: "input_text",
                    text: imageIngredientsPrompt,
                },
                ...imageContent,
            ],
        },
    ];

    let response;
    try {
        response = await client.responses.create({
            model: DEFAULT_OPENAI_MODEL,
            input,
            max_output_tokens: 1800,
        });
    } catch (error) {
        throw new ApiError(502, error?.message || "Failed to scan images with OpenAI");
    }

    const rawText = extractTextFromResponse(response);
    if (!rawText) {
        throw new ApiError(502, "OpenAI returned an empty response");
    }

    return parseModelJson(rawText);
};


// scan Ingredients
let ingredientPrompt=`
    Analyze the image.

    Detect every visible ingredient.

    Rules:
    - Return JSON only.
    - Do not generate recipes.
    - Do not generate cooking instructions.
    - Do not guess hidden ingredients.

    Output:

    {
    "ingredients":[]
    }
    `;
export const scanIngredients = async (imageContent) => {
    const client = await getOpenAIClient();
    
    const response = await client.responses.create({
        model: DEFAULT_OPENAI_MODEL,

        input: [
            {
                role: "user",
                content: [
                    {
                        type: "input_text",
                        text: ingredientPrompt,
                    },
                    ...imageContent,
                ],
            },
        ],

        max_output_tokens: 500,

        text: {
            format: {
                type: "json_object",
            },
        },
    });
   
    let result= JSON.parse(extractTextFromResponse(response));
    if (!Array.isArray(result.ingredients)) {
    return [];
}

return result.ingredients;
};
// GenerateRecipe

export const generateRecipes = async (ingredients) => {
     
    const client = await getOpenAIClient();

    const response = await client.responses.create({

        model: DEFAULT_OPENAI_MODEL,

        input: createRecipePrompt(ingredients),

        max_output_tokens: 2000,

        text: {
            format: {
                type: "json_object",
            },
        },
    });
    
    return JSON.parse(extractTextFromResponse(response));
};

const scanAndGenerateRecipes = async (files) => {
    const client = await getOpenAIClient();

    const imageContent = await Promise.all(
        files.map(async (file) => ({
            type: "input_image",
            image_url: await toOpenAiImageDataUrl(file),
        }))
    );

    try {
        const response = await client.responses.create({
            model: DEFAULT_OPENAI_MODEL,
            input: [
                {
                    role: "user",
                    content: [
                        {
                            type: "input_text",
                            text: singlePassScanAndRecipePrompt,
                        },
                        ...imageContent,
                    ],
                },
            ],
            max_output_tokens: 1600,
            text: {
                format: {
                    type: "json_object",
                },
            },
        });

        const rawText = extractTextFromResponse(response);
        if (!rawText) {
            throw new ApiError(502, "OpenAI returned an empty response");
        }

        const parsed = JSON.parse(rawText);
        let ingredients = Array.isArray(parsed.ingredients) ? parsed.ingredients : [];

        // Normalize string ingredients to object format if needed
        ingredients = ingredients.map((item) => {
            if (typeof item === "string") {
                return {
                    ingredient_name: item,
                    brand: "",
                    estimated_quantity: "1",
                    quantity_unit: "unit",
                    confidence: 0.95,
                };
            }
            return item;
        });

        const recipes = Array.isArray(parsed.recipes)
            ? parsed.recipes
            : Array.isArray(parsed.recipe)
            ? parsed.recipe
            : [];

        if (!ingredients.length) {
            return {
                status: "failed",
                success: false,
                message: "No ingredients were detected in the uploaded image(s).",
                ingredients: [],
                recipes: [],
            };
        }

        if (!recipes.length) {
            return {
                status: "failed",
                success: false,
                message: "No recipes were generated from the detected ingredients.",
                ingredients,
                recipes: [],
            };
        }

        return {
            status: "success",
            success: true,
            ingredients,
            recipes,
            fallback_message: parsed.fallback_message || "",
        };

    } catch (error) {
        throw new ApiError(502, error?.message || "Failed to scan image and generate recipes");
    }
};

const parseRecipeModelJson = (rawText) => {
    const fenced = rawText.match(/```(?:json)?\s*([\s\S]*?)```/i)?.[1];
    let candidate = (fenced || rawText).trim();

    candidate = candidate
        .replace(/^```json\s*/i, "")
        .replace(/^```\s*/i, "")
        .replace(/\s*```$/, "")
        .trim();

    const start = candidate.indexOf("{");
    const end = candidate.lastIndexOf("}");
    if (start === -1 || end === -1 || end <= start) {
        throw new ApiError(502, "OpenAI returned invalid JSON");
    }

    try {
        return JSON.parse(candidate.slice(start, end + 1));
    } catch {
        throw new ApiError(502, "OpenAI returned invalid JSON");
    }
};

export const promptForFourRecipies = async (prompt) => {
    if (!prompt || typeof prompt !== "string") {
        throw new ApiError(400, "prompt must be a non-empty string");
    }

    const client = await getOpenAIClient();

    let response;
    try {
        response = await client.responses.create({
            model: DEFAULT_OPENAI_MODEL,
            input: prompt,
            max_output_tokens: 18000,
            text: {
                format: { type: "json_object" },
            },
        });
    } catch (error) {
        throw new ApiError(502, error?.message || "Failed to get response from OpenAI");
    }

    const rawText = extractTextFromResponse(response);
    if (!rawText) {
        throw new ApiError(502, "OpenAI returned an empty response");
    }

    const parsed = parseRecipeModelJson(rawText);
    return normalizeRecipeResponse(parsed);
};

export const normalizeRecipeForCooked = (r) => {
  if (!r || typeof r !== "object") return r;

  const name = r.name || r.title || "Delicious Dish";
  const description = r.description || "";

  const rawPrep = r.prepTime || r.prep_time || (r.time_and_servings?.prep_time) || "10";
  const prepTime = parseInt(String(rawPrep).replace(/\D/g, ""), 10) || 10;

  const rawCook = r.cookTime || r.cook_time || (r.time_and_servings?.cook_time) || "15";
  const cookTime = parseInt(String(rawCook).replace(/\D/g, ""), 10) || 15;

  const rawKcal = r.kcal || r.calories || (r.nutrition?.calories) || "400";
  const kcal = parseInt(String(rawKcal).replace(/\D/g, ""), 10) || 400;

  const rawServings = r.servings || (r.time_and_servings?.servings) || "2";
  const servings = parseInt(String(rawServings).replace(/\D/g, ""), 10) || 2;

  const cuisine = r.cuisine || (r.metadata?.cuisine) || "International";
  const categories = Array.isArray(r.categories)
    ? r.categories
    : [r.meal_type || (r.metadata?.meal_type) || "Main"];

  const rawIngs = r.ingredients || [];
  const ingredients = rawIngs.map((i) => {
    if (typeof i === "string") return { name: i, quantity: "-", icon: "🍳" };
    return {
      name: i.name || i.ingredient_name || i.ingredient || "",
      quantity: i.quantity || i.estimated_quantity || i.amount || "-",
      icon: i.icon || "🍳",
    };
  });

  const rawSteps = r.steps || r.instructions || [];
  const steps = rawSteps.map((s) => (typeof s === "string" ? s : s.description || s.step || ""));

  const equipment = Array.isArray(r.equipment) ? r.equipment : [];
  const tips = typeof r.tips === "string" ? r.tips : description || "Store leftovers in an airtight container for up to 3 days.";

  return {
    name,
    description,
    prepTime,
    cookTime,
    servings,
    kcal,
    cuisine,
    categories,
    ingredients,
    steps,
    equipment,
    tips,
    image: r.image || r.image_url || null,
  };
};

export const generateRecipesFromIngredients = async (ingredients, userPreferences = {}) => {
  const client = await getOpenAIClient();

  const ingList = Array.isArray(ingredients) ? ingredients.join(", ") : ingredients;
  const prompt = `You are RecipeAI Pro. Generate 3 distinct, delicious recipes using ONLY these ingredients: ${ingList}.
User Preferences: ${JSON.stringify(userPreferences)}

STRICT SPEED & FORMAT RULES:
- Keep recipe description under 20 words.
- Limit instructions to 4-5 concise steps (max 15 words per step).
- Return ONLY valid JSON:
{
  "recipes": [
    {
      "name": "Recipe Name",
      "description": "Short appetizing description",
      "prepTime": "10 mins",
      "cookTime": "15 mins",
      "totalTime": "25 mins",
      "servings": "2",
      "kcal": "450",
      "cuisine": "Italian",
      "categories": ["Dinner"],
      "ingredients": [
        { "name": "Tomato", "quantity": "2" }
      ],
      "steps": [
        "1. Slice tomatoes.",
        "2. Cook in pan."
      ],
      "equipment": ["Pan"],
      "tips": "Pro tip on storage."
    }
  ]
}`;

  try {
    const response = await client.responses.create({
      model: DEFAULT_OPENAI_MODEL,
      input: [
        {
          role: "user",
          content: [{ type: "input_text", text: prompt }],
        },
      ],
      max_output_tokens: 1000,
      text: { format: { type: "json_object" } },
    });

    const rawText = extractTextFromResponse(response);
    const parsed = parseModelJson(rawText);
    const rawList = Array.isArray(parsed.recipes) ? parsed.recipes : [];
    return rawList.map(normalizeRecipeForCooked);
  } catch (error) {
    throw new ApiError(502, error?.message || "Failed to generate recipes from ingredients");
  }
};

export const generateRecipeSuggestions = async (userPreferences = {}) => {
  const client = await getOpenAIClient();

  const prompt = `You are RecipeAI Pro. Generate 3 fast personalized recipe recommendations based on user preferences:
User Preferences: ${JSON.stringify(userPreferences)}

STRICT SPEED & FORMAT RULES:
- Keep recipe description under 20 words.
- Limit instructions to 4 concise steps (max 15 words per step).
- Return ONLY valid JSON:
{
  "recipes": [
    {
      "name": "Recipe Name",
      "description": "Short appetizing description",
      "prepTime": "10 mins",
      "cookTime": "15 mins",
      "totalTime": "25 mins",
      "servings": "2",
      "kcal": "400",
      "cuisine": "Mediterranean",
      "categories": ["Dinner"],
      "ingredients": [
        { "name": "Olive Oil", "quantity": "1 tbsp" }
      ],
      "steps": [
        "1. Prep ingredients.",
        "2. Cook and serve."
      ],
      "equipment": ["Bowl"],
      "tips": "Great for meal prep."
    }
  ]
}`;

  try {
    const response = await client.responses.create({
      model: DEFAULT_OPENAI_MODEL,
      input: [
        {
          role: "user",
          content: [{ type: "input_text", text: prompt }],
        },
      ],
      max_output_tokens: 900,
      text: { format: { type: "json_object" } },
    });

    const rawText = extractTextFromResponse(response);
    const parsed = parseModelJson(rawText);
    const rawList = Array.isArray(parsed.recipes) ? parsed.recipes : [];
    return rawList.map(normalizeRecipeForCooked);
  } catch (error) {
    throw new ApiError(502, error?.message || "Failed to generate initial recipe suggestions");
  }
};

export const generateTrendingDishes = () => {
  return [
    "Chicken Tacos",
    "Pasta Carbonara",
    "Caesar Salad",
    "Sushi Roll",
    "Beef Stir Fry",
    "Avocado Toast",
    "Ramen Bowl",
    "Margarita Pizza",
  ];
};

export const generateDishImage = async (dishName) => {
  return `https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=800&q=80`;
};

export const openAiService = {
    extractRecipeFromCaption,
    scanWithAI,
    scanAndGenerateRecipes,
    promptForFourRecipies,
    normalizeRecipeForCooked,
    generateRecipesFromIngredients,
    generateRecipeSuggestions,
    generateTrendingDishes,
    generateDishImage,
};

