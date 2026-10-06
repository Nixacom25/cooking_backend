package com.cooked.backend.service;

import com.cooked.backend.dto.response.RevenueSummaryResponse;

/** Revenue and subscription metrics for the admin backoffice. */
public interface AdminRevenueService {

    /** Summary for the last 30 days vs the previous 30, plus 30-day and 8-month series. */
    RevenueSummaryResponse getSummary();
}
