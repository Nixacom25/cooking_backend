package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecipeFlagResponse(UUID id, UUID recipeId, String reason, String note, String createdBy, LocalDateTime createdAt,
                                 LocalDateTime resolvedAt, String resolvedBy) {
}
