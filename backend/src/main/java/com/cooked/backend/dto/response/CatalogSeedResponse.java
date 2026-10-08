package com.cooked.backend.dto.response;

/** Result of an idempotent seeding action (starter pack, recipe backfill). */
public record CatalogSeedResponse(int created, int skipped) {
}
