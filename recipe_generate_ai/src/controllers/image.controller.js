import fs from "fs/promises";
import { ApiError } from "../utils/ApiError.js";
import { ApiResponse } from "../utils/ApiResponse.js";
import { asyncHandler } from "../utils/AsyncHandler.js";
import { openAiService } from "../services/openai.service.js";



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

export const scanIngredients = asyncHandler(async (req, res) => {
  const files = Array.isArray(req.files)
    ? req.files
    : req.files && typeof req.files === "object"
    ? Object.values(req.files).flat()
    : req.file
    ? [req.file]
    : [];

  if (!files?.length) {
    throw new ApiError(400, "No images provided");
  }

  try {
   
    const result = await openAiService.scanAndGenerateRecipes(files);
    
    if (result.status !== "success" && result.success !== true) {
        throw new ApiError(
            result.statusCode || 422,
            result.message || result.error?.message || "Failed to extract ingredients",
            result.error ? [result.error] : []
        );
    }
    
    // if (!Array.isArray(result.ingredients) || !result.ingredients.length) {
    //     throw new ApiError(422, "No ingredients were detected in the uploaded image(s).");
    // }

    if (!Array.isArray(result.recipes) || !result.recipes.length) {
        throw new ApiError(502, "No recipes were generated from the detected ingredients.");
    }

    // 📋 Affichage clair des ingrédients détectés dans le terminal
    console.log("\n==================================================");
    console.log("🔍 INGRÉDIENTS DÉTECTÉS :");
    console.log("--------------------------------------------------");
    if (result.ingredients && result.ingredients.length > 0) {
      result.ingredients.forEach((ing, index) => {
        const name = ing.ingredient_name || ing.name || (typeof ing === 'string' ? ing : 'Inconnu');
        const qty = ing.estimated_quantity || ing.quantity || "";
        const unit = ing.quantity_unit || ing.unit || "";
        const brand = ing.brand ? ` (${ing.brand})` : "";
        const confidence = ing.confidence ? ` - Confiance: ${Math.round(ing.confidence * 100)}%` : "";
        console.log(`  ${index + 1}. ${name}${brand} ${qty ? `[Qté: ${qty} ${unit}]` : ""}${confidence}`);
      });
    } else {
      console.log("  (Aucun ingrédient spécifique détecté)");
    }

    console.log("--------------------------------------------------");
    console.log(`🍳 RECETTES GÉNÉRÉES (${result.recipes.length}) :`);
    console.log("--------------------------------------------------");
    result.recipes.forEach((rec, index) => {
      console.log(`  ${index + 1}. ${rec.title} [Temps total: ${rec.total_time || rec.time_and_servings?.total_time || 'N/A'}] - ${rec.cuisine || 'Cuisine'}`);
    });
    console.log("==================================================\n");

    // Format allowed_ingredients for Spring Boot & Mobile DTO
    const rawIngs = Array.isArray(result.ingredients) ? result.ingredients : [];
    const allowedIngredients = rawIngs.map((i) => {
      if (typeof i === "string") return { name: i, quantity: "-" };
      return {
        name: i.ingredient_name || i.name || "",
        quantity: i.estimated_quantity || i.quantity || "-",
      };
    });

    // Normalize recipes for frontend & backend compatibility
    const recipes = (result.recipes || []).map(openAiService.normalizeRecipeForCooked);

    return res.status(200).json({
      success: true,
      allowed_ingredients: allowedIngredients,
      restricted_ingredients: [],
      image_url: null,
      recipes,
    });
  } finally {
    await Promise.allSettled(
      files.map(file => (file?.path ? fs.unlink(file.path) : Promise.resolve()))
    );
  }
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

