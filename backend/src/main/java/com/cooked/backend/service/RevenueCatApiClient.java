package com.cooked.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side RevenueCat REST API (secret key). Used to verify a user's premium
 * entitlement before trusting it, and to grant premium from a
 * redeemed gift code, so the entitlement lives in RevenueCat - the app's
 * source of truth for premium - and not only in our database.
 */
@Slf4j
@Component
public class RevenueCatApiClient {

    private static final String BASE_URL = "https://api.revenuecat.com/v1";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${revenuecat.api.secret:}")
    private String secretApiKey;

    @Value("${revenuecat.entitlement.id:premium}")
    private String entitlementId;

    public boolean isConfigured() {
        return secretApiKey != null && !secretApiKey.isBlank();
    }

    /**
     * Grants a promotional entitlement. Throws if RevenueCat rejects the call,
     * so the caller's transaction (marking the code redeemed) rolls back.
     */
    public void grantPromotionalEntitlement(String appUserId, String duration) {
        if (!isConfigured()) {
            throw new IllegalStateException("RevenueCat secret API key is not configured (REVENUECAT_SECRET_API_KEY)");
        }
        String url = BASE_URL + "/subscribers/" + UriUtils.encodePathSegment(appUserId, StandardCharsets.UTF_8)
                + "/entitlements/" + UriUtils.encodePathSegment(entitlementId, StandardCharsets.UTF_8)
                + "/promotional";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(secretApiKey);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(Map.of("duration", duration), headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("RevenueCat promotional grant failed: " + response.getStatusCode());
        }
        log.info("Granted promotional '{}' ({}) to RevenueCat user {}", entitlementId, duration, appUserId);
    }

    /** The premium entitlement as RevenueCat sees it. expiresAt null = lifetime. */
    public record PremiumEntitlement(String productId, Instant expiresAt, boolean trial) {
        public boolean isActiveAt(Instant now) {
            return expiresAt == null || expiresAt.isAfter(now);
        }
    }

    /**
     * Reads the subscriber's premium entitlement straight from RevenueCat with
     * the secret key - the only trustworthy answer to "did this user pay?".
     * Returns empty when the user has no premium entitlement (or it lapsed,
     * grace period included). Throws when RevenueCat can't be reached, so the
     * caller never mistakes an outage for "not subscribed".
     */
    public Optional<PremiumEntitlement> fetchActivePremium(String appUserId) {
        if (!isConfigured()) {
            throw new IllegalStateException("RevenueCat secret API key is not configured (REVENUECAT_SECRET_API_KEY)");
        }
        String url = BASE_URL + "/subscribers/" + UriUtils.encodePathSegment(appUserId, StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretApiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new IllegalStateException("RevenueCat subscriber lookup failed: " + response.getStatusCode());
        }
        return parsePremium(body, entitlementId, Instant.now());
    }

    static Optional<PremiumEntitlement> parsePremium(JsonNode body, String entitlementId, Instant now) {
        JsonNode subscriber = body.path("subscriber");
        JsonNode ent = subscriber.path("entitlements").path(entitlementId);
        if (ent.isMissingNode() || ent.isNull()) return Optional.empty();

        Instant expires = parseInstant(ent.path("expires_date"));
        Instant grace = parseInstant(ent.path("grace_period_expires_date"));
        boolean lifetime = ent.path("expires_date").isNull();
        // Billing grace period: the store keeps access open while it retries.
        Instant effective = lifetime ? null
                : (grace != null && (expires == null || grace.isAfter(expires)) ? grace : expires);
        if (!lifetime && effective == null) return Optional.empty();

        String productId = ent.path("product_identifier").asText(null);
        boolean trial = productId != null
                && "trial".equalsIgnoreCase(subscriber.path("subscriptions").path(productId).path("period_type").asText(""));

        PremiumEntitlement premium = new PremiumEntitlement(productId, effective, trial);
        return premium.isActiveAt(now) ? Optional.of(premium) : Optional.empty();
    }

    private static Instant parseInstant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) return null;
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            try {
                return java.time.OffsetDateTime.parse(node.asText()).toInstant();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
