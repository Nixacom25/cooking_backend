package com.cooked.backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionRequiredFilterTest {

    @Test
    void readingAndAccountActionsStayFree() {
        assertFalse(SubscriptionRequiredFilter.requiresSubscription("GET", "/recipes"));
        assertFalse(SubscriptionRequiredFilter.requiresSubscription("GET", "/grocery-items"));
        assertFalse(SubscriptionRequiredFilter.requiresSubscription("DELETE", "/recipes/123"));
        assertFalse(SubscriptionRequiredFilter.requiresSubscription("PUT", "/user/me"));
        assertFalse(SubscriptionRequiredFilter.requiresSubscription("POST", "/user/sync-subscription"));
        assertFalse(SubscriptionRequiredFilter.requiresSubscription("POST", "/errors/critical"));
    }

    @Test
    void premiumActionsNeedASubscription() {
        assertTrue(SubscriptionRequiredFilter.requiresSubscription("POST", "/recipes/scan"));
        assertTrue(SubscriptionRequiredFilter.requiresSubscription("POST", "/recipes/import"));
        assertTrue(SubscriptionRequiredFilter.requiresSubscription("POST", "/cookbooks"));
        assertTrue(SubscriptionRequiredFilter.requiresSubscription("PUT", "/grocery-items/1/toggle"));
    }
}
