package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ActivityLogResponse {
    private UUID id;
    private String title;
    private String message;
    private LocalDateTime createdAt;
}
