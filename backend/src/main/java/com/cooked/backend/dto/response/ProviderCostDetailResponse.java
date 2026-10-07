package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One provider over the last {@link #days} days (USD). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCostDetailResponse {
    private String provider;
    private int days;
    private double periodTotal;
    private double periodTotalPrev;
    private CostOverviewResponse.ProviderRow summary;
    private List<CostOverviewResponse.DayAmount> daily;
    private List<CostOverviewResponse.DayAmount> dailyPrev;
    /** Billing-API line items (model / SKU), or manual entries. */
    private List<LineItem> lineItems;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class LineItem {
        private String label;
        private double amount;
        /** 0-100 of the period total. */
        private double share;
    }
}
