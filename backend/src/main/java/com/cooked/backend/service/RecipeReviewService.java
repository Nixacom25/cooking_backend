package com.cooked.backend.service;

import com.cooked.backend.dto.response.ReviewChangesResponse;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeAssignment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks what an intern changes in a recipe: a snapshot is taken before their work, the review screen
 * compares it with the current recipe, and a send-back records which parts were approved / must be fixed.
 */
public interface RecipeReviewService {

    /** Recipe parts compared, in display order. */
    List<String> FIELDS = List.of("name", "image", "servings", "times", "nutrition", "cuisine", "categories",
            "tips", "ingredients", "steps", "equipment", "price");

    Map<String, String> snapshot(Recipe recipe);

    /** Snapshot at assignment time. */
    void captureBaseline(RecipeAssignment assignment);

    /** Called before a recipe is saved: active assignments without a snapshot get one now (late). */
    void captureBaselineIfMissing(Recipe recipe);

    /** Called when the recipe is sent back: remembers the decision and the recipe as it was. */
    void recordSendBack(RecipeAssignment assignment, List<String> fieldsToFix, List<String> fieldsApproved);

    ReviewChangesResponse changes(UUID assignmentId);

    List<String> fieldsToFix(RecipeAssignment assignment);
}
