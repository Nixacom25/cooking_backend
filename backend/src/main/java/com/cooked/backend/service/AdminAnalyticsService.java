package com.cooked.backend.service;

import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.EngagementResponse;
import com.cooked.backend.dto.response.ProductFailuresResponse;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.dto.response.ProductAnalyticsResponse;

/** Read-only product and acquisition analytics for the admin backoffice. */
public interface AdminAnalyticsService {

    int MAX_DAYS = 90;
    int MAX_PAGE_SIZE = 100;

    /** Scan / import / web-search metrics over the last [days] days (clamped to 1..{@value #MAX_DAYS}). */
    ProductAnalyticsResponse product(int days);

    /** Same, counting only events of the users in [segment]. */
    ProductAnalyticsResponse product(int days, com.cooked.backend.dto.request.AnalyticsSegment segment);

    /** Sign-ups and discovery sources over the last [days] days (clamped to 1..{@value #MAX_DAYS}). */
    AcquisitionResponse acquisition(int days);

    /** Daily / weekly / monthly active users over the last [days] days (clamped to 1..{@value #MAX_DAYS}). */
    EngagementResponse engagement(int days);

    /** Same, for the users in [segment] (total users = segment size). */
    EngagementResponse engagement(int days, com.cooked.backend.dto.request.AnalyticsSegment segment);

    /** Failed [type] events over the last [days] days, newest first (size clamped to 1..{@value #MAX_PAGE_SIZE}). */
    ProductFailuresResponse failures(ProductEventType type, int days, int page, int size);
}
