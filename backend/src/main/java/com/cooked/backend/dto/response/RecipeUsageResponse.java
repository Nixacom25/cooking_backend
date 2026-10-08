package com.cooked.backend.dto.response;

/** Usage of one recipe (Recipe detail → performance cards). */
public record RecipeUsageResponse(long views, long views30d, long cookbooks, long mealPlans, long groceryAdds, long openReports, long assignments) {
}
