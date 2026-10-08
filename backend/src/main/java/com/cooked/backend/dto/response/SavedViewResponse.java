package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record SavedViewResponse(UUID id, String screen, String name, String query, LocalDateTime createdAt) {
}
