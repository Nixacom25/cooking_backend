package com.cooked.backend.service;

import com.cooked.backend.dto.request.IngredientPayload;

import java.util.List;
import java.util.Optional;

/**
 * Maps a free-text ingredient name (e.g. what the photo scan AI saw) to an ingredient that exists in the
 * catalog, so the app never shows a name the database does not know.
 */
public interface IngredientNameResolver {

    /** The catalog name for this text ("fresh tomatoes" → "Tomato"), or empty when nothing in the catalog matches. */
    Optional<String> resolve(String rawName);

    /**
     * Only the ingredients the catalog knows, renamed to their catalog name, one per name (first wins). Null in,
     * null out; unknown names are dropped.
     */
    List<IngredientPayload> keepKnown(List<IngredientPayload> detected);
}
