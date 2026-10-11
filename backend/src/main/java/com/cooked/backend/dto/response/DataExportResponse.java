package com.cooked.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One export as listed under "Previous exports". */
public record DataExportResponse(UUID id, String status, List<String> datasets, String format,
                                 LocalDate fromDay, LocalDate toDay, int progress, Long sizeBytes,
                                 String requestedBy, String requestedByName, LocalDateTime createdAt,
                                 LocalDateTime finishedAt, LocalDateTime expiresAt, String error) {
}
