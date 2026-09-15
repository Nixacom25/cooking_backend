package com.cooked.backend.service;

/**
 * One-time job: tags every existing recipe photo with structured metadata
 * and inserts it into the image library, so tag-based matching has
 * something to work with from day one instead of starting empty.
 */
public interface ImageLibraryBackfillService {
    /** Returns the number of new image_library rows created. */
    int backfillFromExistingRecipes();
}
