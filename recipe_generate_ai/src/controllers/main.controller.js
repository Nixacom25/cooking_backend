import { ApiResponse } from "../utils/ApiResponse.js";
import { asyncHandler } from "../utils/AsyncHandler.js";
import { ApiError } from "../utils/ApiError.js";
import { tiktokService } from "../services/tiktok.service.js";
import { instagramService } from "../services/instagram.service.js";
import { openAiService } from "../services/openai.service.js";
import { youtubeService } from "../services/youtube.service.js";
import { webService } from "../services/web.service.js";

export const main = asyncHandler(async (req, res) => {
  const { url } = req.body ?? {};
  if (!url) {
    throw new ApiError(
      400,
      "Missing 'url' in request body"
    );
  }

  const isTikTok = /(?:tiktok\.com|vt\.tiktok\.com)/i.test(url);
  const isInstagram = /(?:instagram\.com|instagr\.am)/i.test(url);
  const isYoutube = /(?:youtube\.com|youtu\.be)/i.test(url);
  const isUnsupportedNetwork = /(?:linkedin\.com|twitter\.com|x\.com|snapchat\.com|reddit\.com|threads\.net)/i.test(url);

  if (isUnsupportedNetwork) {
    throw new ApiError(
      400,
      "Your content is not supported. Please use recipes from TikTok, Instagram, YouTube, etc."
    );
  }

  let data;
  if (isTikTok) {
    data = await tiktokService(url);
  } else if (isInstagram) {
    data = await instagramService(url);
  } else if (isYoutube) {
    data = await youtubeService(url);
  } else {
    data = await webService(url);
  }

  if (!data) {
    throw new ApiError(500, "Failed to process URL");
  }

  const description = data?.description?.trim?.() || "";
  if (!description) {
    throw new ApiError(422, "No content found to extract a recipe. Your content is not a recipe.");
  }

  const aiResponse = await openAiService.extractRecipeFromCaption(description);

  // Format matching old AI microservice & Spring Boot backend expectations
  const rawRecipe = aiResponse?.recipe || aiResponse || {};

  // Extract ingredients cleanly, handling objects, arrays, and string items
  let ingredients = [];
  const rawIngredients = rawRecipe.ingredients || rawRecipe.recipeIngredient || [];
  if (Array.isArray(rawIngredients)) {
    ingredients = rawIngredients.map(ing => {
      if (typeof ing === 'string') {
        const trimmed = ing.trim();
        return trimmed.length > 0 ? { name: trimmed, quantity: '1 unit', icon: '🍳' } : null;
      }
      if (ing && typeof ing === 'object') {
        const name = ing.name || ing.ingredient_name || ing.ingredient || ing.item || '';
        const quantity = ing.quantity || ing.estimated_quantity || ing.amount || '1 unit';
        const icon = ing.icon || '🍳';
        const cleanName = String(name).trim();
        return cleanName.length > 0 ? { name: cleanName, quantity: String(quantity).trim(), icon: String(icon).trim() } : null;
      }
      return null;
    }).filter(Boolean);
  }

  // Extract steps cleanly, handling strings, numbers, and step objects
  let steps = [];
  const rawSteps = rawRecipe.instructions || rawRecipe.steps || rawRecipe.recipeInstructions || rawRecipe.method || [];
  if (Array.isArray(rawSteps)) {
    steps = rawSteps.map(step => {
      if (typeof step === 'string') return step.trim();
      if (step && typeof step === 'object') {
        return String(step.text || step.step || step.instruction || step.description || '').trim();
      }
      return '';
    }).filter(s => s.length > 0);
  } else if (typeof rawSteps === 'string' && rawSteps.trim().length > 0) {
    steps = rawSteps.split('\n').map(s => s.trim()).filter(s => s.length > 0);
  }

  const mappedRecipe = {
    name: rawRecipe.title || rawRecipe.name || "Recette sans titre",
    image: rawRecipe.image || data.thumbnail || null,
    cookTime: parseInt(rawRecipe.cookTime || rawRecipe.cook_time || rawRecipe.time_and_servings?.cook_time, 10) || 0,
    prepTime: parseInt(rawRecipe.prepTime || rawRecipe.prep_time || rawRecipe.time_and_servings?.prep_time, 10) || 0,
    kcal: parseInt(rawRecipe.kcal || rawRecipe.calories || rawRecipe.nutrition?.calories, 10) || 0,
    servings: parseInt(rawRecipe.servings || rawRecipe.time_and_servings?.servings, 10) || 1,
    tips: rawRecipe.tips || rawRecipe.description || "",
    cuisine: rawRecipe.cuisine || rawRecipe.metadata?.cuisine || "International",
    category: (Array.isArray(rawRecipe.categories) ? rawRecipe.categories[0] : (rawRecipe.category || rawRecipe.metadata?.meal_type)) || "Plat Principal",
    ingredients,
    steps,
    equipment: rawRecipe.equipment || [],
    sourceUrl: url,
    origin: 'IMPORT'
  };

  const payload = {
    source: data.platform || "web",
    image: data.thumbnail || mappedRecipe.image,
    recipe: {
      status: aiResponse?.status || "success",
      chef_persona: aiResponse?.chef_persona || "Chef",
      recipe: mappedRecipe,
      fallback_message: aiResponse?.fallback_message || ""
    }
  };

  return res
    .status(200)
    .json(new ApiResponse(200, payload, "Recipe extracted successfully"));
});