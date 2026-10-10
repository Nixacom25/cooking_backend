package com.cooked.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RevenueCatApiClientTest {

    private static final Instant NOW = Instant.parse("2026-10-10T19:00:00Z");

    private static JsonNode json(String s) throws Exception {
        return new ObjectMapper().readTree(s);
    }

    @Test
    void activeYearlyAfterTrial() throws Exception {
        JsonNode body = json("""
                {"subscriber":{"entitlements":{"premium":{"expires_date":"2027-10-02T05:23:44Z",
                  "grace_period_expires_date":null,"product_identifier":"cooked_yearly"}},
                 "subscriptions":{"cooked_yearly":{"period_type":"normal"}}}}""");
        Optional<RevenueCatApiClient.PremiumEntitlement> p = RevenueCatApiClient.parsePremium(body, "premium", NOW);
        assertTrue(p.isPresent());
        assertEquals("cooked_yearly", p.get().productId());
        assertEquals(Instant.parse("2027-10-02T05:23:44Z"), p.get().expiresAt());
        assertFalse(p.get().trial());
    }

    @Test
    void trialIsFlagged() throws Exception {
        JsonNode body = json("""
                {"subscriber":{"entitlements":{"premium":{"expires_date":"2026-10-12T00:00:00Z","product_identifier":"m"}},
                 "subscriptions":{"m":{"period_type":"trial"}}}}""");
        assertTrue(RevenueCatApiClient.parsePremium(body, "premium", NOW).get().trial());
    }

    @Test
    void expiredEntitlementIsNotActive() throws Exception {
        JsonNode body = json("""
                {"subscriber":{"entitlements":{"premium":{"expires_date":"2026-10-02T05:23:18Z","product_identifier":"m"}}}}""");
        assertTrue(RevenueCatApiClient.parsePremium(body, "premium", NOW).isEmpty());
    }

    @Test
    void gracePeriodKeepsAccess() throws Exception {
        JsonNode body = json("""
                {"subscriber":{"entitlements":{"premium":{"expires_date":"2026-10-09T00:00:00Z",
                  "grace_period_expires_date":"2026-10-20T00:00:00Z","product_identifier":"m"}}}}""");
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"),
                RevenueCatApiClient.parsePremium(body, "premium", NOW).get().expiresAt());
    }

    @Test
    void lifetimeHasNoExpiry() throws Exception {
        JsonNode body = json("""
                {"subscriber":{"entitlements":{"premium":{"expires_date":null,"product_identifier":"rc_promo_premium_lifetime"}}}}""");
        assertNull(RevenueCatApiClient.parsePremium(body, "premium", NOW).get().expiresAt());
    }

    @Test
    void noEntitlement() throws Exception {
        assertTrue(RevenueCatApiClient.parsePremium(json("{\"subscriber\":{\"entitlements\":{}}}"), "premium", NOW).isEmpty());
    }
}
