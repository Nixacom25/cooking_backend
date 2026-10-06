package com.cooked.backend.service;

import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.ProductAnalyticsResponse;

/** Read-only product and acquisition analytics for the admin backoffice. */
public interface AdminAnalyticsService {

    int MAX_DAYS = 90;

    /** Scan / import / web-search metrics over the last [days] days (clamped to 1..{@value #MAX_DAYS}). */
    ProductAnalyticsResponse product(int days);

    /** Sign-ups and discovery sources over the last [days] days (clamped to 1..{@value #MAX_DAYS}). */
    AcquisitionResponse acquisition(int days);
}
