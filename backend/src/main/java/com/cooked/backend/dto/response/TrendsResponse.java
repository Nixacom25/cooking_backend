package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What is rising in Cooked over the last {@link #days} days vs the window before. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendsResponse {
    private int days;
    /** Today's AI-selected trending dishes (shown in the app's search). */
    private List<String> trendingDishes;
    /** Fastest-growing Cooked search with its daily series, null when no search yet. */
    private Focus focus;
    private List<Trend> searches;
    /** Ingredients in recipes created from scans. */
    private List<Trend> ingredients;
    /** Recipes added to users' libraries (all origins), by name. */
    private List<Trend> recipes;
    private List<Trend> categories;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Trend {
        private String label;
        private long total;
        private long previous;
        /** % change vs the previous window, null when it is new (previous = 0). */
        private Double growth;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Focus {
        private String label;
        private long total;
        private long previous;
        private Double growth;
        private List<AcquisitionResponse.DayCount> daily;
        private List<AcquisitionResponse.DayCount> dailyPrev;
    }
}
