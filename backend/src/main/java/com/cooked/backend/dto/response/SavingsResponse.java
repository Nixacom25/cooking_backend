package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * "Your Savings" payload. Computed entirely server-side from the user's
 * SCAN recipes only, so the mobile app just renders it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsResponse {
    private double totalSaved;
    private int recipeCount;
    private List<Item> recipes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private RecipeResponse recipe;
        private double savings;
        /** Recipe name, prefixed with "(Copy) " when the same name already appeared earlier in the list. */
        private String displayName;
    }
}
