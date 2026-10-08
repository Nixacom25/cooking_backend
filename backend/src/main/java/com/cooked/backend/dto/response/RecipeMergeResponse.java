package com.cooked.backend.dto.response;

import java.util.UUID;

/** What moved from the archived duplicate to the recipe kept. */
public record RecipeMergeResponse(UUID kept, UUID archived, int cookbooks, int mealPlans, int groceryItems) {
}
