import { ApiError } from "../utils/ApiError.js";
import { ApiResponse } from "../utils/ApiResponse.js";
import { asyncHandler } from "../utils/AsyncHandler.js";
import { openAiService } from "../services/openai.service.js";
import { scanFridge } from "../services/scan.service.js";



export const extractIngredientNames = (result) => {
    if (Array.isArray(result)) {
        return result
            .map((item) => (typeof item === "string" ? item : item?.ingredient_name))
            .filter(Boolean);
    }

    if (!result || typeof result !== "object") {
        throw new ApiError(502, "Invalid ingredient scan response");
    }

    if (!result.success) {
        throw new ApiError(
            422,
            result.error?.message || result.message || "Failed to extract ingredients",
            result.error ? [result.error] : []
        );
    }

    if (!Array.isArray(result.ingredients)) {
        throw new ApiError(502, "Ingredients must be an array");
    }

    const ingredientNames = result.ingredients
        .map((item) => (typeof item === "string" ? item : item?.ingredient_name))
        .filter(Boolean);

    if (!ingredientNames.length) {
        throw new ApiError(422, "No ingredients were detected in the uploaded image(s).");
    }

    return ingredientNames;
};

const parsePreferences = (raw) => {
  if (!raw) return {};
  if (typeof raw === "object") return raw;
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
};

export const scanIngredients = asyncHandler(async (req, res) => {
  const files = Array.isArray(req.files)
    ? req.files
    : req.files && typeof req.files === "object"
    ? Object.values(req.files).flat()
    : req.file
    ? [req.file]
    : [];

  if (!files.length) {
    throw new ApiError(400, "No images provided");
  }

  const prefs = parsePreferences(req.body?.user_preferences);
  const { ingredients, restricted, recipes } = await scanFridge(files, prefs);

  return res.status(200).json({
    success: true,
    allowed_ingredients: ingredients.map((i) => ({
      name: i.ingredient_name,
      quantity: i.estimated_quantity || "-",
    })),
    restricted_ingredients: restricted.map((i) => ({
      name: i.ingredient_name,
      icon: "🚫",
      reason: i.allergen_reason,
    })),
    image_url: null,
    recipes: recipes.map(openAiService.normalizeRecipeForCooked),
  });
});


export const generateImageHandler = asyncHandler(async (req, res) => {
  const name = req.body.name || req.body.title || "";
  if (!name || typeof name !== "string") {
    throw new ApiError(400, "Missing or invalid 'name' in request body");
  }

  const imageUrl = await openAiService.generateDishImage(name);

  return res.status(200).json({
    success: true,
    image_url: imageUrl,
  });
});

