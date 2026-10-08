package com.cooked.backend.service;

import com.cooked.backend.dto.request.AppEventsRequest;
import com.cooked.backend.dto.request.SiteVisitRequest;

/** Events reported by clients: the mobile app (signed in) and the public website (anonymous page views). */
public interface AppEventService {

    /** Records the allowed app events; returns how many were kept (unknown types and over-limit events are dropped). */
    int record(AppEventsRequest request, String userEmail);

    /** Records a website page view (rate limited per visitor). */
    void visit(SiteVisitRequest request);
}
