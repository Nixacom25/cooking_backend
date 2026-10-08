package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupportMacroResponse(UUID id, String title, String body, String createdBy, LocalDateTime updatedAt) {
}
