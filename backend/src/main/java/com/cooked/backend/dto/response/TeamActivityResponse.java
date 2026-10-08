package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Last time a team member used Cooked (latest of their sessions and app activity). */
public record TeamActivityResponse(UUID userId, String email, LocalDateTime lastActive) {
}
