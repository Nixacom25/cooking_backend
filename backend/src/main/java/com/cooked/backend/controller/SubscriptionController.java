package com.cooked.backend.controller;

import com.cooked.backend.dto.request.SubscriptionPaymentRequest;
import com.cooked.backend.dto.request.IapReceiptRequest;
import com.cooked.backend.entity.User;
import com.cooked.backend.service.PaywallService;
import com.cooked.backend.service.SubscriptionService;
import com.cooked.backend.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/subscriptions")
@Tag(name = "Subscription", description = "Dynamic Paywall & Subscription Management")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PaywallService paywallService;
    private final UserRepository userRepository;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  PaywallService paywallService,
                                  UserRepository userRepository) {
        this.subscriptionService = subscriptionService;
        this.paywallService = paywallService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Get current user subscription")
    @GetMapping("/me")
    public ResponseEntity<?> getMySubscription(Authentication auth) {
        return ResponseEntity.ok(subscriptionService.getMySubscription(auth.getName()));
    }

    @Operation(summary = "Get payment history")
    @GetMapping("/history")
    public ResponseEntity<?> getPaymentHistory(Authentication auth) {
        return ResponseEntity.ok(subscriptionService.getPaymentHistory(auth.getName()));
    }

    @Operation(summary = "Process subscription payment")
    @PostMapping("/pay")
    public ResponseEntity<?> paySubscription(Authentication auth, @RequestBody SubscriptionPaymentRequest request) {
        return ResponseEntity.ok(subscriptionService.paySubscription(auth.getName(), request));
    }

    @Operation(summary = "Verify IAP Receipt")
    @PostMapping("/verify-receipt")
    public ResponseEntity<?> verifyReceipt(Authentication auth, @RequestBody IapReceiptRequest request) {
        return ResponseEntity.ok(subscriptionService.verifyReceipt(auth.getName(), request));
    }

    @Operation(summary = "Get subscription plan details")
    @GetMapping("/plan")
    public ResponseEntity<?> getPlan() {
        return ResponseEntity.ok(subscriptionService.getPlan());
    }

    @Operation(summary = "Get Dynamic Paywall Config (A/B Testing or Specific Flow)")
    @GetMapping({"/paywall-config", "/api/subscription/paywall-config"})
    public ResponseEntity<?> getPaywallConfig(
            Authentication auth,
            @RequestParam(required = false) String flow) {
        
        if ("OFFER".equalsIgnoreCase(flow)) {
             return ResponseEntity.ok(paywallService.getOfferConfiguration());
        }

        if (auth == null || auth.getName() == null) {
            // Fallback for anonymous or failed auth - return variant A
            return ResponseEntity.ok(paywallService.getDefaultConfiguration());
        }
        User user = userRepository.findByEmail(auth.getName())
                .orElse(null);
        
        if (user == null) {
             return ResponseEntity.ok(paywallService.getDefaultConfiguration());
        }
        return ResponseEntity.ok(paywallService.getConfigurationForUser(user));
    }

    @Operation(summary = "Get current subscription status (legacy support)")
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of(
            "subscriptionStatus", user.getSubscriptionStatus(),
            "subscriptionType", user.getSubscriptionType(),
            "expiresAt", user.getSubscriptionExpiresAt() != null ? user.getSubscriptionExpiresAt() : "",
            "isPremium", subscriptionService.isPremium(user)
        ));
    }
}
