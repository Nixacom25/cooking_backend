package com.cooked.backend.dto.response;

import com.cooked.backend.service.monitoring.RequestMetrics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Live health of the platform for the admin "System health" screen.
 *
 * @param metrics          this backend's HTTP metrics over the last 15 minutes
 * @param dbLatencyMs      time of a trivial query, null when the database did not answer
 * @param workerFailures24h failed scheduled jobs in the last 24 h
 * @param services         one row per monitored service
 * @param openIncidents    incidents declared and not resolved
 */
public record SystemStatusResponse(LocalDateTime at, RequestMetrics.Snapshot metrics, List<RequestMetrics.Minute> lastMinutes,
                                   Integer dbLatencyMs, long workerFailures24h, List<Service> services, List<IncidentResponse> openIncidents) {

    /** status: UP, DEGRADED, DOWN, IDLE (no traffic) or NOT_CONFIGURED. */
    public record Service(String name, String status, String detail) {
    }
}
