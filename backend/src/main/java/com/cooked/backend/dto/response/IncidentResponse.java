package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record IncidentResponse(UUID id, String title, String message, String severity, String status,
                               String createdBy, LocalDateTime createdAt, LocalDateTime resolvedAt) {
}
