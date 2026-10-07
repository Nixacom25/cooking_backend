package com.cooked.backend.service;

import com.cooked.backend.dto.response.RevenueSummaryResponse;

/** Revenue and subscription metrics for the admin backoffice. */
public interface AdminRevenueService {

    /**
     * Summary for the last {@code days} days (clamped to 1..365) vs the same
     * length just before, plus a daily series (at least 7 days, at most 90)
     * and an 8-month series. Field names keep their historic "30" suffix.
     */
    RevenueSummaryResponse getSummary(int days);
}
