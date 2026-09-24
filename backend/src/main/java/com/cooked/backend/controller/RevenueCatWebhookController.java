package com.cooked.backend.controller;

import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.PushNotificationService;
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
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;

    @Value("${revenuecat.webhook.secret:}")
    private String webhookSecret;

    // Valid product IDs
    private static final String MONTHLY_PRODUCT_ID = "monthly_sub";
    private static final String YEARLY_PRODUCT_ID = "yearly_sub";

    public RevenueCatWebhookController(UserRepository userRepository, EmailService emailService,
            PushNotificationService pushNotificationService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.pushNotificationService = pushNotificationService;
    }

    private boolean isValidProduct(String productId) {
        return MONTHLY_PRODUCT_ID.equals(productId) || YEARLY_PRODUCT_ID.equals(productId);
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

            // Validate product ID
            if (productId != null && !isValidProduct(productId)) {
                log.warn("Invalid product ID in webhook: {}", productId);
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid product ID"));
            }

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
                "NON_RENEWING_PURCHASE".equalsIgnoreCase(eventType) ||
                // TRANSFER's app_user_id is the receiving account, and
                // SUBSCRIPTION_EXTENDED grants extra time (goodwill/support) -
                // both carry a fresh expiration_at_ms, so they activate the
                // same as a purchase/renewal.
                "TRANSFER".equalsIgnoreCase(eventType) ||
                "SUBSCRIPTION_EXTENDED".equalsIgnoreCase(eventType)) {

                // Check if this is a trial (INITIAL_PURCHASE may be trial)
                Boolean isTrial = null;
                if ("INITIAL_PURCHASE".equalsIgnoreCase(eventType)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> product = (Map<String, Object>) event.get("product");
                    if (product != null) {
                        isTrial = (Boolean) product.get("is_trial");
                    }
                }

                // Set status based on trial detection
                if (isTrial != null && isTrial) {
                    user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
                } else {
                    user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                }

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
                log.info("Activated subscription (status: {}) for user: {}", user.getSubscriptionStatus(), user.getEmail());

            } else if ("EXPIRATION".equalsIgnoreCase(eventType) || "CANCELLATION".equalsIgnoreCase(eventType) ||
                    // A pause (Google Play only) and a refund both revoke
                    // access the same way an expiration does.
                    "SUBSCRIPTION_PAUSED".equalsIgnoreCase(eventType) || "REFUND".equalsIgnoreCase(eventType)) {
                user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                userRepository.save(user);
                log.info("Set subscription EXPIRED for user: {} (event: {})", user.getEmail(), eventType);
            } else if ("BILLING_ISSUE".equalsIgnoreCase(eventType)) {
                String planName = productId != null && productId.toLowerCase().contains("year") ? "Yearly" : "Monthly";
                String price = formatPrice(event.get("price"), event.get("currency"));
                emailService.sendPaymentFailureEmail(user.getEmail(), user.getFirstname(), planName, price);
                // Billing alerts only respect the master push switch, not the
                // reminders/news sub-toggles - losing access to the app is
                // account-critical, not optional marketing.
                if (user.isPushEnabled()) {
                    pushNotificationService.sendPush(user.getFcmToken(), "Payment failed",
                            "We couldn't process your payment for your " + planName + " plan. Update your billing details to avoid losing access.",
                            Map.of("type", "billing_issue"));
                }
                log.info("Sent payment-failure email for user: {}", user.getEmail());
            }

            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (Exception e) {
            log.error("Error processing RevenueCat webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private String formatPrice(Object price, Object currency) {
        if (price == null) {
            return "your plan price";
        }
        String currencyCode = currency != null ? currency.toString() : "USD";
        return String.format("%.2f %s", ((Number) price).doubleValue(), currencyCode);
    }
}
