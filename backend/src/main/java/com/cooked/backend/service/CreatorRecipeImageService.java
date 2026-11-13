package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.entity.RecipeGeneratedImage;
import com.cooked.backend.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * AI recipe photos, saved for admin review. Creators: gpt-image photo of each recipe
 * they generate. Clients: a fal FLUX.2 Klein photo only when the image library has no
 * verified match (client.ai-images.enabled). Off with creator.ai-images.enabled=false.
 */
public interface CreatorRecipeImageService {

    /** True for creators while the feature is on. */
    boolean isEnabledFor(User user);

    /**
     * For a creator, replaces every recipe's image with an AI photo of that
     * recipe, generated in parallel. A photo that isn't ready in time gets a
     * link ({@link #photoLink}) that serves it as soon as it is; the recipes
     * themselves are always kept. Does nothing for other users.
     */
    void attachImages(List<CreateRecipeRequest> recipes, String userEmail);

    /**
     * Where the photo link of AI photo [id] should point, waiting for it while
     * it is still being made; null for an unknown id. Never the placeholder:
     * if every attempt failed it is the recipe's previous image.
     */
    PhotoLink photoLink(UUID id);

    /** [done] = the AI photo itself, safe to cache for good. */
    record PhotoLink(String url, boolean done) {
    }

    /** True for app clients (role CLIENT) while client photos are on and FAL_KEY is set. */
    boolean clientPhotosEnabledFor(User user);

    /**
     * A client's saved recipe has no verified library photo: starts a fal (FLUX.2 Klein) photo of that
     * exact recipe in the background and returns its photo link at once ({@link #photoLink}), which the
     * recipe can show right away; the photo itself replaces the link when ready. Null when off or failed.
     */
    String startClientPhoto(UUID recipeId, UUID userId, com.cooked.backend.service.dto.RecipeImageInput input);

    /** A recipe was saved with [imageUrl]: links the AI photo to it, if it is one. */
    void linkSavedRecipe(UUID recipeId, String imageUrl);

    /**
     * Makes a new photo from the same recipe data as [source] (admin action).
     * Returns the new row at once; the photo is generated in the background
     * and replaces [source] on the saved recipe when it succeeds.
     */
    RecipeGeneratedImage regenerate(RecipeGeneratedImage source);

    /** Old "Cooked photo unavailable" image; no longer given to new recipes. */
    String placeholderUrl();
}
