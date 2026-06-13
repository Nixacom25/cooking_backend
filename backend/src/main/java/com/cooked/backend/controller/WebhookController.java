package com.cooked.backend.controller;

import com.cooked.backend.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final SubscriptionService subscriptionService;

    public WebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/apple")
    public ResponseEntity<Void> handleAppleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received Apple App Store Webhook notification");
        try {
            String signedPayload = (String) payload.get("signedPayload");
            if (signedPayload != null) {
                subscriptionService.handleAppleWebhook(signedPayload);
            } else {
                log.warn("Apple webhook did not contain signedPayload field");
            }
        } catch (Exception e) {
            log.error("Error processing Apple webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build(); // Always return 200 OK to Apple to avoid retries
    }

    @PostMapping("/google")
    public ResponseEntity<Void> handleGoogleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received Google Play Developer Webhook notification");
        try {
            subscriptionService.handleGoogleWebhook(payload);
        } catch (Exception e) {
            log.error("Error processing Google webhook: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build(); // Always return 200 OK to Google to avoid retries
    }
}
