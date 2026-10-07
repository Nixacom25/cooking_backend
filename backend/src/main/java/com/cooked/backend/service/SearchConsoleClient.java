package com.cooked.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Google Search Console search analytics (read-only). */
public interface SearchConsoleClient {

    record Row(String key, double clicks, double impressions, double ctr, double position) {}

    boolean isConfigured();

    String siteUrl();

    /** Totals for [from, to] (empty when not configured or unavailable). */
    Optional<Row> totals(LocalDate from, LocalDate to);

    /** Rows by page, best first (empty when not configured or unavailable). */
    List<Row> byPage(LocalDate from, LocalDate to, int limit);
}
