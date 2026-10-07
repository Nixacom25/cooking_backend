package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Cost Center overview in USD: totals, daily series, categories, providers and alerts. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostOverviewResponse {
    private int days;
    private String currency;
    private LocalDate today;

    private double costToday;
    private double costYesterday;
    private double monthToDate;
    /** Same number of days of the previous month. */
    private double monthToDatePrev;
    private double lastMonthTotal;
    private double projectedMonth;
    /** Sum of provider monthly budgets, null when none is set. */
    private Double totalBudget;
    private Double costPerActiveUser;
    private Double costPerSubscriber;

    private List<DayAmount> daily;
    private List<DayAmount> dailyPrev;
    private List<LabelAmount> byCategory;
    private List<ProviderRow> providers;
    private List<Alert> alerts;

    /** OpenAI billing sync configured (OPENAI_ADMIN_KEY). */
    private boolean openAiSync;
    private LocalDate lastSyncedDay;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class DayAmount {
        private String date;
        private double amount;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class LabelAmount {
        private String label;
        private double amount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProviderRow {
        private String provider;
        private String category;
        /** MANUAL (cost entries), SYNCED (billing API) or MIXED. */
        private String source;
        private double monthToDate;
        private double projectedMonth;
        private double periodTotal;
        private Double monthlyBudget;
        private Double creditBalance;
        private String creditUnit;
        private LocalDateTime creditUpdatedAt;
        /** Days of USD credits left at the last 7 days' pace, null when unknown. */
        private Integer creditDaysLeft;
        private LocalDate creditExhaustion;
        /** HEALTHY, OVER_BUDGET, LOW_CREDITS or NO_BUDGET. */
        private String status;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Alert {
        /** OVER_BUDGET or LOW_CREDITS. */
        private String kind;
        private String provider;
        private String title;
        private String message;
    }
}
