package com.cooked.backend.service;

import com.cooked.backend.entity.User;

import java.time.Duration;

/**
 * Server-side check of a user's premium access against the store
 * (RevenueCat), used instead of trusting what the app reports.
 */
public interface SubscriptionVerificationService {

    /** True when the store can be queried (RevenueCat secret key configured). */
    boolean isAvailable();

    /**
     * Asks RevenueCat whether the user has an active premium entitlement and,
     * if so, writes it to the user (status, expiry, plan). Never downgrades:
     * losing access is driven by webhooks and the stored expiry date.
     * Calls for the same user closer than {@code minInterval} are skipped and
     * return false, so a client can't hammer RevenueCat through us.
     *
     * @return true if the store confirms an active entitlement
     */
    boolean refreshFromStore(User user, Duration minInterval);
}
