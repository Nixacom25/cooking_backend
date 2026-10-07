package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutomationResponse {
    private String key;
    private String name;
    /** SCHEDULED or EVENT. */
    private String kind;
    private String trigger;
    private List<String> actions;
    private LocalDateTime lastRunAt;
    private Boolean lastRunSuccess;
    private String lastError;
    private long runs30d;
    private long failures30d;
    private Double avgDurationMs;
    /** Limitation worth knowing (e.g. "only logs"), null otherwise. */
    private String note;
}
