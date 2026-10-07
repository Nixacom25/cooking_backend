package com.cooked.backend.service;

/**
 * Imports daily spend from one provider's billing API into provider_daily_costs.
 * Add a bean per provider; the Cost Center and the nightly job pick them all up.
 */
public interface ProviderBillingSync {

    /** Provider name as shown in the Cost Center ("OpenAI"). */
    String provider();

    /** False when the credentials are not configured (the sync is then skipped). */
    boolean isConfigured();

    /** Replaces the last [days] days of spend; returns the number of rows stored. */
    int sync(int days);
}
