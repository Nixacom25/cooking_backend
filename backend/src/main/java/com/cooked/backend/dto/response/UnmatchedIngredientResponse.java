package com.cooked.backend.dto.response;

import com.cooked.backend.entity.IngredientNameSource;
import com.cooked.backend.entity.UnmatchedStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** A Not-in-catalog name with the closest existing visual (suggestion is null when nothing is close). */
public record UnmatchedIngredientResponse(UUID id, String name, String key, long seen, int users,
                                          IngredientNameSource source, long scanCount, long importCount,
                                          long groceryCount, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt,
                                          UnmatchedStatus status, Suggestion suggestion, UUID resolvedVisualId) {

    public record Suggestion(UUID id, String canonicalId, String name, double score) {
    }
}
