'use strict';

const { tiktokService } = require('../services/tiktok.service');
const { instagramService } = require('../services/instagram.service');
const { openaiService } = require('../services/openai.service');
const { youtubeService } = require('../services/youtube.service');
const { webService } = require('../services/web.service');

/**
 * Extracts recipe data from a social media or web URL.
 * 
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {import('express').NextFunction} next
 */
async function extractRecipe(req, res, next) {
  try {
    const { url } = req.body ?? {};
    if (!url) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'BAD_REQUEST',
          message: "L'URL est manquante dans le corps de la requête"
        }
      });
    }

    let data;
    const isTikTok = /tiktok\.com/i.test(url);
    const isInstagram = /(?:instagram\.com|instagr\.am)/i.test(url);
    const isYoutube = /(?:youtube\.com|youtu\.be)/i.test(url);

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
      return res.status(500).json({
        success: false,
        error: {
          code: 'EXTRACTION_FAILED',
          message: "Échec du traitement de l'URL"
        }
      });
    }

    const description = data?.description?.trim?.() || "";
    if (!description) {
      return res.status(422).json({
        success: false,
        error: {
          code: 'NO_DESCRIPTION',
          message: "Aucune description trouvée pour extraire la recette"
        }
      });
    }

    // Call OpenAI service to parse the description into a recipe
    const aiResponse = await openaiService.extractRecipeFromCaption(description);

    if (!aiResponse || aiResponse.status === 'failure') {
      return res.status(422).json({
        success: false,
        error: {
          code: 'EXTRACTION_FAILED',
          message: aiResponse?.fallback_message || "Impossible d'identifier une recette dans la description."
        }
      });
    }

    // Map the Recipie App prompt structure to the structure expected by the backend
    const rawRecipe = aiResponse.recipe || {};
    const mappedRecipe = {
      name: rawRecipe.title || rawRecipe.name || "Recette sans titre",
      image: rawRecipe.image || data.thumbnail || null,
      cookTime: parseInt(rawRecipe.time_and_servings?.cook_time || rawRecipe.cookTime, 10) || 0,
      prepTime: parseInt(rawRecipe.time_and_servings?.prep_time || rawRecipe.prepTime, 10) || 0,
      kcal: parseInt(rawRecipe.time_and_servings?.kcal || rawRecipe.kcal, 10) || 0,
      servings: parseInt(rawRecipe.time_and_servings?.servings || rawRecipe.servings, 10) || 1,
      tips: rawRecipe.description || rawRecipe.tips || "",
      cuisine: rawRecipe.metadata?.cuisine || rawRecipe.cuisine || "Inconnue",
      category: rawRecipe.metadata?.meal_type || rawRecipe.category || "Plat Principal",
      ingredients: (rawRecipe.ingredients || []).map(ing => ({
        name: String(ing.name || '').trim(),
        quantity: String(ing.quantity || '').trim(),
        icon: String(ing.icon || '').trim()
      })),
      steps: rawRecipe.instructions || rawRecipe.steps || [],
      equipment: rawRecipe.equipment || [],
      sourceUrl: url,
      origin: 'IMPORT'
    };

    // Construct response matching Recipie App format
    const payload = {
      source: data.platform,
      image: data.thumbnail || mappedRecipe.image,
      recipe: {
        status: aiResponse.status,
        chef_persona: aiResponse.chef_persona,
        recipe: mappedRecipe,
        fallback_message: aiResponse.fallback_message
      }
    };

    return res.status(200).json({
      success: true,
      data: payload,
      message: 'Recette extraite avec succès'
    });
  } catch (error) {
    next(error);
  }
}

module.exports = { extractRecipe };