package com.cooked.backend.service;

import com.cooked.backend.entity.GiftPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Minimal Stripe client over the REST API (no SDK dependency):
 * creates hosted Checkout sessions for website gifts and verifies webhook
 * signatures.
 *
 * Configuration (Render env):
 * STRIPE_SECRET_KEY (sk_live_... / sk_test_...), STRIPE_WEBHOOK_SECRET
 * (whsec_...), GIFT_SITE_URL (defaults to https://cookedapp.com).
 */
@Slf4j
@Component
public class StripeClient {

    private static final String API = "https://api.stripe.com/v1";
    /** Reject webhook events signed more than 5 minutes ago (replay protection). */
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${gift.site-url:https://cookedapp.com}")
    private String siteUrl;

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    /**
     * Creates a Stripe-hosted Checkout page for a gift. The amount comes
     * only from {@link GiftPlan#getWebPriceCents()} - never from the client.
     *
     * @return the Checkout URL to redirect the buyer to
     */
    public String createGiftCheckout(GiftPlan plan, int quantity, String purchaserEmail, String recipientEmail,
                                     String senderName) {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe is not configured (STRIPE_SECRET_KEY)");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("customer_email", purchaserEmail);
        form.add("success_url", siteUrl + "/gift/success");
        form.add("cancel_url", siteUrl + "/gift?canceled=1");
        form.add("line_items[0][quantity]", String.valueOf(quantity));
        form.add("line_items[0][price_data][currency]", "usd");
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(plan.getWebPriceCents()));
        form.add("line_items[0][price_data][product_data][name]", "Cooked Premium gift - " + plan.getLabel());
        form.add("metadata[type]", "gift");
        form.add("metadata[plan]", plan.name());
        form.add("metadata[quantity]", String.valueOf(quantity));
        if (recipientEmail != null) {
            form.add("metadata[recipient_email]", recipientEmail);
        }
        if (senderName != null && !senderName.isBlank()) {
            form.add("metadata[sender_name]", senderName);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBearerAuth(secretKey);

        ResponseEntity<String> response = restTemplate.exchange(
                API + "/checkout/sessions", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
        try {
            JsonNode session = objectMapper.readTree(response.getBody());
            String url = session.path("url").asText(null);
            if (url == null) throw new IllegalStateException("Stripe returned no checkout URL");
            return url;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Stripe response", e);
        }
    }

    /**
     * Verifies the Stripe-Signature header (HMAC-SHA256 of "timestamp.payload"
     * with the endpoint secret) and returns the parsed event.
     *
     * @throws SecurityException when the signature is missing, wrong or too old
     */
    public JsonNode verifyWebhook(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new SecurityException("Stripe webhook secret is not configured (STRIPE_WEBHOOK_SECRET)");
        }
        if (payload == null || signatureHeader == null) {
            throw new SecurityException("Missing Stripe signature");
        }
        String timestamp = null;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) timestamp = kv[1];
            if (kv[0].equals("v1")) signatures.add(kv[1]);
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new SecurityException("Malformed Stripe signature");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new SecurityException("Malformed Stripe timestamp");
        }
        if (Math.abs(Instant.now().getEpochSecond() - ts) > SIGNATURE_TOLERANCE_SECONDS) {
            throw new SecurityException("Stripe signature too old");
        }

        byte[] expected = hmacSha256(webhookSecret, timestamp + "." + payload);
        boolean valid = signatures.stream().anyMatch(sig -> {
            try {
                return MessageDigest.isEqual(expected, HexFormat.of().parseHex(sig));
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
        if (!valid) throw new SecurityException("Invalid Stripe signature");

        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new SecurityException("Invalid Stripe payload");
        }
    }

    static byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
