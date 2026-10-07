package com.cooked.backend.service;

import com.cooked.backend.dto.response.TrendsResponse;

/** Trend intelligence from Cooked's own data (searches, scans, saved recipes, categories). */
public interface AdminTrendsService {

    int MAX_DAYS = 90;

    TrendsResponse trends(int days);
}
