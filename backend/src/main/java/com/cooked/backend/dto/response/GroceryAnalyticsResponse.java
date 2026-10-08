package com.cooked.backend.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Grocery lists over the last [days] days (and the same length just before).
 *
 * @param bought      items of the window already ticked as bought
 * @param fromRecipes items added from a recipe (vs typed by hand)
 * @param usersEver   users who ever added something to a grocery list
 */
public record GroceryAnalyticsResponse(int days, long itemsAdded, long itemsAddedPrev, long users, long usersPrev, long bought, long fromRecipes,
                                       long usersEver, List<AcquisitionResponse.DayCount> daily, List<AcquisitionResponse.DayCount> dailyPrev,
                                       List<Label> topIngredients, List<RecipeItem> topRecipes) {

    public record Label(String label, long total) {
    }

    public record RecipeItem(UUID id, String name, long total) {
    }
}
