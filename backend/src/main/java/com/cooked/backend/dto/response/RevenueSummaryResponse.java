package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Revenue & subscription summary for the admin backoffice (Subscriptions and
 * Revenue screens). Amounts are in {@link #currency}. Windows: last 30 days
 * vs the 30 days before.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueSummaryResponse {
    private String currency;
    /** Length of the window behind the "...30" fields (days). */
    private int periodDays;

    private long activeSubscriptions;
    private long activeTrials;
    private long monthlyPlans;
    private long yearlyPlans;
    private long cancelled;
    private long expired;

    private double mrr;
    private double arr;
    private double revenue30;
    private double revenuePrev30;

    private long payments30;
    private long renewals30;
    private long billingFailures30;
    private long refunds30;

    /** Share (%) of everyone who ever paid that is now cancelled or expired; null when nobody paid yet. */
    private Double churnRate;
    /** Average lifetime revenue per paying user; null when nobody paid yet. */
    private Double ltv;

    /** Revenue (30 days) per store, highest first. */
    private Map<String, Double> byStore;
    /** Revenue (30 days) per plan label (Monthly / Annual / Gift / Other), highest first. */
    private Map<String, Double> byPlan;

    /** One point per day for the last 30 days (zero-filled). */
    private List<DailyAmount> daily;
    /** One point per month for the last 8 months (zero-filled). */
    private List<MonthlyAmount> monthly;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyAmount {
        /** ISO date, e.g. 2026-10-06. */
        private String date;
        private double amount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MonthlyAmount {
        /** ISO year-month, e.g. 2026-10. */
        private String month;
        private double amount;
    }
}
