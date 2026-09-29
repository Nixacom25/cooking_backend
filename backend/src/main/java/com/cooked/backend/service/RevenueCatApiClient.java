package com.cooked.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Server-side RevenueCat REST API (secret key). Used to grant premium from a
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
}
