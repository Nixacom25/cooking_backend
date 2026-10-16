package com.cooked.backend.service;

import java.util.List;
import java.util.Optional;

/** Builds the Open Graph images of share links (see ShareController). */
public interface ShareImageService {

    /** Number of recipe images a cookbook collage is made of. */
    int COLLAGE_SIZE = 4;

    /**
     * 1200x630 JPEG with the first {@link #COLLAGE_SIZE} images in a 2x2 grid,
     * or empty when fewer than that many could be downloaded and decoded.
     */
    Optional<byte[]> collage(List<String> imageUrls);
}
