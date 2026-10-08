package com.cooked.backend.service;

import com.cooked.backend.dto.response.AdminDashboardResponse;

/** Headline counters for the admin dashboard. */
public interface AdminDashboardService {

    AdminDashboardResponse getMetrics();

    /** Today's grocery adds and planned meals (vs yesterday), AI requests in progress, new subscribers in the last hour. */
    com.cooked.backend.dto.response.DashboardLiveResponse live();
}
