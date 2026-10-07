package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationDetailResponse {
    private IntegrationStatusResponse summary;
    /** Success rate over the last 7 days (0-100), null without events. */
    private Double successRate7d;
    private Double successRatePrev7d;
    private Double avgLatencyMs7d;
    private List<NameStats> byName;
    private List<Event> events;
    private long totalEvents;
    private int page;
    private int totalPages;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class NameStats {
        private String name;
        private long total;
        private long failures;
        private LocalDateTime lastAt;
    }

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class Event {
        private UUID id;
        private String name;
        private boolean success;
        private Integer httpStatus;
        private Integer latencyMs;
        private String detail;
        private LocalDateTime createdAt;
    }
}
