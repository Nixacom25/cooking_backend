package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record IngredientReleaseResponse(UUID id, int version, LocalDateTime publishedAt, String publishedBy,
                                        int ingredientCount, String notes, boolean downloadable) {
}
