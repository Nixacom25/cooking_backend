package com.cooked.backend.controller;

import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@Tag(name = "RevenueCat Webhook", description = "Endpoints for processing RevenueCat subscription events")
public class RevenueCatWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RevenueCatWebhookController.class);

    private final UserRepository userRepository;

    @Value("${revenuecat.webhook.secret:}")
    private String webhookSecret;

    public RevenueCatWebhookController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(summary = "Handle RevenueCat Subscription Webhook Event")
    @PostMapping({"/subscriptions/revenuecat-webhook", "/webhooks/revenuecat"})
    public ResponseEntity<?> handleWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {
        log.info("Received RevenueCat webhook payload: {}", payload);

        if (webhookSecret != null && !webhookSecret.trim().isEmpty()) {
            if (authHeader == null || authHeader.trim().isEmpty()) {
                log.warn("RevenueCat Webhook rejected: Missing Authorization header");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing Authorization header"));
            }
            String cleanAuth = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
            String cleanSecret = webhookSecret.startsWith("Bearer ") ? webhookSecret.substring(7).trim() : webhookSecret.trim();

            if (!cleanAuth.equals(cleanSecret)) {
                log.warn("RevenueCat Webhook rejected: Invalid Authorization header token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized webhook caller"));
            }
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) payload.get("event");
            if (event == null) {
                log.warn("RevenueCat payload does not contain an 'event' object");
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid event payload"));
            }

            String eventType = (String) event.get("type");
            String appUserId = (String) event.get("app_user_id");
            String productId = (String) event.get("product_id");
            String originalTransactionId = (String) event.get("original_transaction_id");
            Number expirationMsNum = (Number) event.get("expiration_at_ms");

            log.info("RevenueCat Event: type={}, appUserId={}, productId={}", eventType, appUserId, productId);

            if (appUserId == null || appUserId.isEmpty()) {
                log.warn("RevenueCat event missing app_user_id");
                return ResponseEntity.ok(Map.of("status", "IGNORED_MISSING_USER_ID"));
            }

            // Attempt to find user by email or UUID
            Optional<User> userOpt = userRepository.findByEmail(appUserId);
            if (userOpt.isEmpty()) {
                try {
                    UUID userId = UUID.fromString(appUserId);
                    userOpt = userRepository.findById(userId);
                } catch (IllegalArgumentException ignored) {}
            }

            if (userOpt.isEmpty()) {
                log.warn("User not found for RevenueCat app_user_id: {}", appUserId);
                return ResponseEntity.ok(Map.of("status", "USER_NOT_FOUND"));
            }

            User user = userOpt.get();

            // Handle event types
            if ("INITIAL_PURCHASE".equalsIgnoreCase(eventType) ||
                "RENEWAL".equalsIgnoreCase(eventType) ||
                "PRODUCT_CHANGE".equalsIgnoreCase(eventType) ||
                "UNCANCELLATION".equalsIgnoreCase(eventType) ||
                "NON_RENEWING_PURCHASE".equalsIgnoreCase(eventType)) {

                user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);

                if (productId != null && productId.toLowerCase().contains("year")) {
                    user.setSubscriptionType(SubscriptionType.YEARLY);
                } else {
                    user.setSubscriptionType(SubscriptionType.MONTHLY);
                }

                if (expirationMsNum != null) {
                    LocalDateTime expirationDate = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(expirationMsNum.longValue()),
                            ZoneId.systemDefault()
                    );
                    user.setSubscriptionExpiresAt(expirationDate);
                }

                if (originalTransactionId != null) {
                    user.setOriginalTransactionId(originalTransactionId);
                }

                userRepository.save(user);
                log.info("Activated subscription for user: {}", user.getEmail());

            } else if ("EXPIRATION".equalsIgnoreCase(eventType) || "CANCELLATION".equalsIgnoreCase(eventType)) {
                user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                userRepository.save(user);
                log.info("Set subscription EXPIRED for user: {}", user.getEmail());
            }

            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (Exception e) {
            log.error("Error processing RevenueCat webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
