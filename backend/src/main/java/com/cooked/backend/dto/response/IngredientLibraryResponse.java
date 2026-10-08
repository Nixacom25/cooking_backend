package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Ingredient Library page: rows, status tab counts (current filters except status), catalog-wide KPIs,
 * filter options and the publish state.
 */
public record IngredientLibraryResponse(List<IngredientVisualResponse> items, long total, int page, int size, int totalPages,
                                        Map<String, Long> tabCounts, Kpis kpis, List<String> categories,
                                        List<String> collections, Release release) {

    public record Kpis(long total, long complete, long needsReview, long missingAsset, long missingAnimation,
                       long disabled, long notInCatalog, long notInCatalogNew7d, long aliases) {
    }

    /** latestVersion = 0 when nothing was published yet. */
    public record Release(int latestVersion, LocalDateTime publishedAt, String publishedBy, int ingredientCount,
                          int nextVersion, long unpublishedChanges) {
    }
}
