package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Active users (app opened that day) over the last {@link #days} days and the
 * window before. History starts at {@link #trackingSince}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngagementResponse {
    private int days;
    /** First recorded activity day, null when none yet. */
    private LocalDate trackingSince;
    /** Client accounts (all time). */
    private long totalUsers;
    /** Distinct active users in the period / the previous one. */
    private long activeUsers;
    private long activeUsersPrev;
    /** Average daily active users in the period / the previous one. */
    private double dau;
    private double dauPrev;
    /** Distinct active users over the last 7 / 30 days, and the window before. */
    private long wau;
    private long wauPrev;
    private long mau;
    private long mauPrev;
    private List<AcquisitionResponse.DayCount> daily;
    private List<AcquisitionResponse.DayCount> dailyPrev;
}
