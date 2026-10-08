package com.cooked.backend.dto.response;

import java.util.List;

/** Not-in-catalog queue page and its header numbers (open names, new this week, sightings in 30 days). */
public record UnmatchedQueueResponse(List<UnmatchedIngredientResponse> items, long total, int page, int size,
                                     int totalPages, long open, long openLast30d, long newLast7d, long seenLast30d,
                                     long ignored, long resolved) {
}
