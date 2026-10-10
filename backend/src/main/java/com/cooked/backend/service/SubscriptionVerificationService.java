package com.cooked.backend.service;

import com.cooked.backend.entity.User;

import java.time.Duration;

/**
 * Server-side check of a user's premium access against the store
 * (RevenueCat), used instead of trusting what the app reports.
 */
public interface SubscriptionVerificationService {

    /** Outcome of a store lookup. */
    enum Result {
        /** RevenueCat confirms an active entitlement (written to the user). */
        CONFIRMED,
        /** RevenueCat answered: no active entitlement. */
        NOT_ENTITLED,
        /** RevenueCat couldn't answer (outage, bad key...): unknown. */
        UNAVAILABLE,
        /** Skipped: checked too recently, or verification not configured. */
        SKIPPED
    }

    /** True when the store can be queried (RevenueCat secret key configured). */
    boolean isAvailable();

    /**
     * Asks RevenueCat whether the user has an active premium entitlement and,
     * if so, writes it to the user (status, expiry, plan). Never downgrades:
     * losing access is driven by webhooks and the stored expiry date.
     * Calls for the same user closer than {@code minInterval} are SKIPPED,
     * so a client can't hammer RevenueCat through us.
     */
    Result verify(User user, Duration minInterval);

    /** @return true if the store confirms an active entitlement */
    default boolean refreshFromStore(User user, Duration minInterval) {
        return verify(user, minInterval) == Result.CONFIRMED;
    }
}
