package com.cooked.backend.controller;

import com.cooked.backend.service.GiftService;
import com.cooked.backend.service.StripeClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Stripe webhook (website gift purchases). Configure in the Stripe
 * dashboard: endpoint https://<api>/webhooks/stripe, event
 * checkout.session.completed; put its signing secret in STRIPE_WEBHOOK_SECRET.
 * Every event is signature-checked before anything happens.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Stripe Webhook", description = "Website gift payments")
public class StripeWebhookController {

    private final StripeClient stripeClient;
    private final GiftService giftService;

    @Operation(summary = "Handle Stripe events (signature verified)")
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<?> handle(@RequestBody String payload,
                                    @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        JsonNode event;
        try {
            event = stripeClient.verifyWebhook(payload, signature);
        } catch (SecurityException e) {
            log.warn("Rejected Stripe webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid signature"));
        }

        String type = event.path("type").asText("");
        if ("checkout.session.completed".equals(type)
                || "checkout.session.async_payment_succeeded".equals(type)) {
            try {
                giftService.createFromWebPurchase(event.path("data").path("object"));
            } catch (Exception e) {
                // Non-2xx makes Stripe retry: a paid gift must never be lost.
                log.error("Failed to create gift from Stripe event {}: {}", event.path("id").asText(), e.getMessage());
                return ResponseEntity.internalServerError().body(Map.of("error", "processing failed"));
            }
        }
        return ResponseEntity.ok(Map.of("received", true));
    }
}
