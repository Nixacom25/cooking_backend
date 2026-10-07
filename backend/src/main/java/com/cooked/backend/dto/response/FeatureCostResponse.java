package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Estimated cost per feature: "AI & APIs" spend allocated to scan / import /
 * web search by weighted call volume (the AI service does not report usage).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureCostResponse {
    private int days;
    private double aiSpend;
    private double aiSpendPrev;
    /** How the split is made, shown under the table. */
    private String basis;
    private List<Feature> features;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Feature {
        private String feature;
        private long requests;
        private long requestsPrev;
        private double weight;
        private double cost;
        private double costPrev;
        /** 0-100 of the allocated spend. */
        private double share;
        private Double costPerUse;
    }
}
