package com.cooked.backend.dto.response;

/** One step of a long import: what this call added and skipped, and how many are still to add (0 = done). */
public record ImportProgressResponse(int created, int skipped, int remaining) {
}
