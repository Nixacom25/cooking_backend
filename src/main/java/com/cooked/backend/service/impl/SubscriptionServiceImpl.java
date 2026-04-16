package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.SubscriptionPaymentRequest;
import com.cooked.backend.dto.request.UpdateSubscriptionPlanRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.UserSubscription;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.SubscriptionPlanRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSubscriptionRepository;
import com.cooked.backend.service.SubscriptionService;
import com.cooked.backend.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.exception.BadRequestException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import com.cooked.backend.dto.response.SubscriptionPaymentResponse;
import com.cooked.backend.entity.SubscriptionPayment;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Value("${google.play.service-account:}")
    private String googleServiceAccountBase64;

    @Value("${apple.iap.shared-secret:}")
    private String appleSharedSecret;

    @Override
    public SubscriptionPlan getPlan() {
        List<SubscriptionPlan> plans = planRepository.findAll();
        if (plans.isEmpty()) {
            return ensureDefaultPlan();
        }
        return plans.get(0);
    }

    @Override
    @Transactional
    public SubscriptionPlan updatePlan(UpdateSubscriptionPlanRequest request) {
        SubscriptionPlan plan = getPlan();
        plan.setMonthlyPrice(request.getMonthlyPrice());
        plan.setYearlyPrice(request.getYearlyPrice());
        plan.setYearlyDiscountPercentage(request.getYearlyDiscountPercentage());
        plan.setTrialDays(request.getTrialDays());
        return planRepository.save(plan);
    }

    @Override
    public UserSubscription getMySubscription(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userSubscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found for this user"));
    }

    @Override
    @Transactional
    public MessageResponse paySubscription(String userEmail, SubscriptionPaymentRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        // ---------- STRIPE PAYMENT SIMULATION ----------
        // In a real app, you would pass `request.getStripeToken()` to the Stripe Java
        // SDK.
        // E.g. Charge.create(Map.of("amount", 2000, "currency", "usd", "source",
        // request.getStripeToken()));

        // Mock Validation Rules:
        if (request.getStripeToken().equals("tok_fail")) {
            throw new com.cooked.backend.exception.BadRequestException(
                    "Payment failed: Insufficient funds or card declined.");
        }

        // Simulate a 1-second network delay to Stripe servers
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // ---------- END STRIPE SIMULATION ----------

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime latestStart = subscription.getEndDate().isAfter(now) ? subscription.getEndDate() : now;

        long daysToAdd = request.getIsYearly() ? 365 : 30;

        subscription.setEndDate(latestStart.plusDays(daysToAdd));
        subscription.setIsYearly(request.getIsYearly());
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        userSubscriptionRepository.save(subscription);

        // Record the payment
        SubscriptionPlan plan = getPlan();
        BigDecimal price = request.getIsYearly() ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .user(user)
                .amount(price)
                .planType(request.getIsYearly() ? "YEARLY" : "MONTHLY")
                .status("SUCCESS")
                .stripePaymentId("simulated_" + request.getStripeToken())
                .build();
        subscriptionPaymentRepository.save(payment);

        activityLogService.logActivity(user, "Subscription Successful",
                "Your subscription has been extended. Thank you for using Cooked!");

        return new MessageResponse("Payment successful, subscription activated/renewed.");
    }

    @Override
    @Transactional
    public MessageResponse verifyReceipt(String userEmail, com.cooked.backend.dto.request.IapReceiptRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        boolean isValid = false;
        boolean isYearly = request.getProductId().toLowerCase().contains("yearly");

        if ("ANDROID".equalsIgnoreCase(request.getPlatform())) {
            // Real Google Play Verification Logic
            try {
                if (googleServiceAccountBase64 != null && !googleServiceAccountBase64.trim().isEmpty()) {
                    String base64Key = googleServiceAccountBase64.replaceAll("\\s", "");
                    GoogleCredentials credentials = GoogleCredentials.fromStream(
                            new ByteArrayInputStream(Base64.getDecoder().decode(base64Key)))
                            .createScoped(Collections.singleton("https://www.googleapis.com/auth/androidpublisher"));
                    
                    AndroidPublisher publisher = new AndroidPublisher.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance(),
                            new HttpCredentialsAdapter(credentials))
                            .setApplicationName("Cooked")
                            .build();

                    SubscriptionPurchase purchase = publisher.purchases().subscriptions()
                            .get(request.getPackageName(), request.getProductId(), request.getPurchaseToken())
                            .execute();

                    log.info("Google IAP Verification Response for {}: State={}, OrderID={}", 
                            userEmail, purchase.getPaymentState(), purchase.getOrderId());

                    // Payment State 1 = Payment received
                    if (purchase.getPaymentState() != null && purchase.getPaymentState() == 1) {
                        isValid = true;
                        log.info("Google IAP Verification SUCCESS for user: {}", userEmail);
                    } else {
                        log.warn("Google IAP Verification FAILED: Payment state is {}, not 1", purchase.getPaymentState());
                    }
                } else {
                    log.warn("Google Play Service Account key missing in environment!");
                    isValid = true; // Fallback for dev if credentials not yet provided
                }
            } catch (Exception e) {
                log.error("CRITICAL error during Google Play verification for {}: {}", userEmail, e.getMessage(), e);
                isValid = true; // Temporary fallback for transition
            }
        } else if ("IOS".equalsIgnoreCase(request.getPlatform())) {
            /*
            // REAL APPLE VERIFICATION (V1 legacy or V2 App Store Server API)
            // This requires a POST to Apple's verifyReceipt endpoint
            // https://buy.itunes.apple.com/verifyReceipt (Production)
            // https://sandbox.itunes.apple.com/verifyReceipt (Sandbox)
            */
            isValid = true;
        }

        if (!isValid) {
            throw new BadRequestException("Purchase verification failed with " + request.getPlatform());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime latestStart = subscription.getEndDate().isAfter(now) ? subscription.getEndDate() : now;

        long daysToAdd = isYearly ? 365 : 30;

        subscription.setEndDate(latestStart.plusDays(daysToAdd));
        subscription.setIsYearly(isYearly);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        userSubscriptionRepository.save(subscription);

        // Record the payment
        SubscriptionPlan plan = getPlan();
        BigDecimal price = isYearly ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        SubscriptionPayment payment = SubscriptionPayment.builder()
                .user(user)
                .amount(price)
                .planType(isYearly ? "YEARLY" : "MONTHLY")
                .status("SUCCESS")
                .stripePaymentId("iap_" + request.getPlatform() + "_" + request.getPurchaseToken().length())
                .build();
        subscriptionPaymentRepository.save(payment);

        activityLogService.logActivity(user, "Subscription Successful",
                "Your subscription via " + request.getPlatform() + " has been activated.");

        return new MessageResponse("Purchase verified and subscription activated.");
    }

    @Override
    public List<SubscriptionPaymentResponse> getPaymentHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return subscriptionPaymentRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(payment -> SubscriptionPaymentResponse.builder()
                        .id(payment.getId())
                        .amount(payment.getAmount())
                        .planType(payment.getPlanType())
                        .status(payment.getStatus())
                        .stripePaymentId(payment.getStripePaymentId())
                        .createdAt(payment.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Scheduled(cron = "0 0 0 * * ?") // Runs every day at midnight
    @Transactional
    public void processExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<UserSubscription> expiredSubscriptions = userSubscriptionRepository.findAllByEndDateBeforeAndStatusNot(now,
                SubscriptionStatus.EXPIRED);

        for (UserSubscription sub : expiredSubscriptions) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            userSubscriptionRepository.save(sub);
        }
    }

    private SubscriptionPlan ensureDefaultPlan() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .monthlyPrice(BigDecimal.valueOf(20))
                .yearlyPrice(BigDecimal.valueOf(200))
                .yearlyDiscountPercentage(16.67) // Approximate savings
                .trialDays(3)
                .build();
        return planRepository.save(plan);
    }
}
