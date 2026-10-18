package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.entity.RecipeGeneratedImage;
import com.cooked.backend.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * AI photos for creator recipes: each recipe a creator generates gets a photo
 * made from the whole recipe, saved for admin review. Everyone else keeps the
 * existing image matching. Turned off with creator.ai-images.enabled=false.
 */
public interface CreatorRecipeImageService {

    /** True for creators while the feature is on. */
    boolean isEnabledFor(User user);

    /**
     * For a creator, replaces every recipe's image with an AI photo of that
     * recipe, generated in parallel (one retry each). A recipe whose photo
     * fails or isn't ready in time gets the "photo unavailable" placeholder;
     * the recipes themselves are always kept. Does nothing for other users.
     */
    void attachImages(List<CreateRecipeRequest> recipes, String userEmail);

    /** A recipe was saved with [imageUrl]: links the AI photo to it, if it is one. */
    void linkSavedRecipe(UUID recipeId, String imageUrl);

    /**
     * Makes a new photo from the same recipe data as [source] (admin action).
     * Returns the new row at once; the photo is generated in the background
     * and replaces [source] on the saved recipe when it succeeds.
     */
    RecipeGeneratedImage regenerate(RecipeGeneratedImage source);

    /** "Cooked photo unavailable" image shown when generation fails. */
    String placeholderUrl();
}
