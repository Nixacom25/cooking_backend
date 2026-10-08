package com.cooked.backend.dto.response;

import com.cooked.backend.entity.ProductEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scan / import / web-search metrics measured by the backend over the last
 * {@link #days} days, plus recipe-creation history (which predates event
 * tracking, see {@link #trackingSince}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAnalyticsResponse {
    private int days;
    /** Distinct users per feature measured from the database: GROCERY (items added), COOKBOOK (cookbooks touched), MEAL_PLAN (meals planned). */
    private java.util.Map<String, Long> featureUsers;
    /** First recorded product event, null when none yet. */
    private LocalDateTime trackingSince;

    private List<TypeStats> summaries;
    private List<DailyCounts> daily;
    private List<LabelStats> importSources;
    private List<LabelStats> topSearches;
    private List<LabelStats> zeroResultSearches;
    private List<FailureReason> failureReasons;
    private List<RecipesCreated> recipesCreated;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TypeStats {
        private ProductEventType type;
        private long total;
        private long successes;
        private long failures;
        /** 0-100, null when no event. */
        private Double successRate;
        private Double avgDurationMs;
        private long users;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyCounts {
        private String date;
        private long scans;
        private long scanFailures;
        private long imports;
        private long importFailures;
        private long searches;
        private long searchFailures;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LabelStats {
        private String label;
        private long total;
        private long failures;
        /** 0-100. */
        private double successRate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FailureReason {
        private ProductEventType type;
        private String reason;
        private long total;
    }

    /** Recipes saved per day from scans and imports (exists for the whole history). */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecipesCreated {
        private String date;
        private long fromScans;
        private long fromImports;
    }
}
