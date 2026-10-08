package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record TicketMessageResponse(UUID id, String kind, String body, String author, LocalDateTime createdAt) {
}
