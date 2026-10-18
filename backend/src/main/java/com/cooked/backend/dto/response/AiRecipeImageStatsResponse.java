package com.cooked.backend.dto.response;

import java.util.List;
import java.util.Map;

/** AI recipe photos over the last [days]: event counts, time, cost and review rates. */
public record AiRecipeImageStatsResponse(
        int days,
        /** recipe_image_generation_started … recipe_image_added_to_database → count. */
        Map<String, Long> events,
        long pendingReview,
        Double avgGenerationMs,
        double totalCostUsd,
        Double costPerImage,
        /** Percentages (0-100), null when there is nothing to measure yet. */
        Double failureRate,
        Double retryRate,
        Double approvalRate,
        Double rejectionRate,
        Map<String, Long> rejectionReasons,
        List<ModelStats> models) {

    public record ModelStats(String model, long generated, long succeeded, long failed, long retried,
                             long approved, long rejected, Double avgGenerationMs, double costUsd,
                             Double approvalRate) {
    }
}
