package com.cooked.backend.dto.response;

import java.util.UUID;

/** Duplicate guard: owner is the visual this name already resolves to (null = free). */
public record IngredientNameCheckResponse(String name, String key, boolean available, Owner owner) {

    public record Owner(UUID id, String canonicalId, String name) {
    }
}
