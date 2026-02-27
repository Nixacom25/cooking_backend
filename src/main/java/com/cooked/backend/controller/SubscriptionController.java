package com.cooked.backend.controller;

import com.cooked.backend.dto.request.SubscriptionPaymentRequest;
import com.cooked.backend.dto.request.UpdateSubscriptionPlanRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.entity.UserSubscription;
import com.cooked.backend.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Subscription Management APIs")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "Get the current subscription plan details (Prices, etc.)")
    @GetMapping("/plan")
    public ResponseEntity<SubscriptionPlan> getPlan() {
        return ResponseEntity.ok(subscriptionService.getPlan());
    }

    @Operation(summary = "Update the subscription plan prices and discounts (Admin Only)")
    @PutMapping("/plan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlan> updatePlan(@Valid @RequestBody UpdateSubscriptionPlanRequest request) {
        return ResponseEntity.ok(subscriptionService.updatePlan(request));
    }

    @Operation(summary = "Get my current subscription status")
    @GetMapping("/me")
    public ResponseEntity<UserSubscription> getMySubscription(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getMySubscription(authentication.getName()));
    }

    @Operation(summary = "Pay/renew subscription")
    @PostMapping("/pay")
    public ResponseEntity<MessageResponse> paySubscription(Authentication authentication,
            @Valid @RequestBody SubscriptionPaymentRequest request) {
        return ResponseEntity.ok(subscriptionService.paySubscription(authentication.getName(), request));
    }
}
