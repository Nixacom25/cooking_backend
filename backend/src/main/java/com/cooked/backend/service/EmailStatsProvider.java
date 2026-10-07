package com.cooked.backend.service;

import com.cooked.backend.dto.response.EmailProviderStats;

import java.time.LocalDate;
import java.util.Optional;

/** Delivery / open / click statistics from the email provider. */
public interface EmailStatsProvider {

    boolean isConfigured();

    /** Stats for [from, to] (inclusive), optionally for one tag; empty when unavailable. */
    Optional<EmailProviderStats> aggregated(LocalDate from, LocalDate to, String tag);
}
