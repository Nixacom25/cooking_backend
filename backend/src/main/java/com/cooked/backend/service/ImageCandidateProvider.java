package com.cooked.backend.service;

import java.util.List;

/**
 * Searches an external image/recipe API for candidate photos matching a
 * tag-derived query. Kept as an interface so the provider (Edamam today)
 * can be swapped without touching the matching pipeline.
 */
public interface ImageCandidateProvider {
    /** Returns up to {@code limit} candidate image URLs, or empty if unavailable/misconfigured. */
    List<String> searchCandidates(String query, int limit);
}
