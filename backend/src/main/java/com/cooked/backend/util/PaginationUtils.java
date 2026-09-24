package com.cooked.backend.util;

/**
 * Every paginated admin/list endpoint builds its own {@code PageRequest.of(page, size, ...)}
 * straight from request params. Spring rejects page &lt; 0 or size &lt; 1 with an
 * uncaught {@link IllegalArgumentException} (500), and an unbounded size lets a
 * caller ask for an arbitrarily expensive query - clamp both before they ever
 * reach {@code PageRequest.of}.
 */
public final class PaginationUtils {

    public static final int MAX_PAGE_SIZE = 100;

    private PaginationUtils() {}

    public static int clampPage(int page) {
        return Math.max(page, 0);
    }

    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
