package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One integration's configuration and recent activity (no secrets). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationStatusResponse {
    private String key;
    private String name;
    private String description;
    private boolean configured;
    /** CONNECTED, WARNING, IDLE (configured, no activity recorded) or NOT_CONFIGURED. */
    private String status;
    private LocalDateTime lastEventAt;
    private long events24h;
    private long failures24h;
    private long events30d;
    private long failures30d;
    /** Where the activity numbers come from ("webhooks", "emails sent", "scan / import / search calls"…). */
    private String activity;
}
