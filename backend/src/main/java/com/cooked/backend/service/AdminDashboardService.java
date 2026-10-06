package com.cooked.backend.service;

import com.cooked.backend.dto.response.AdminDashboardResponse;

/** Headline counters for the admin dashboard. */
public interface AdminDashboardService {

    AdminDashboardResponse getMetrics();
}
