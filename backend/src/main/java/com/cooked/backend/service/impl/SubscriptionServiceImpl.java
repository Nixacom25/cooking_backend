package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.SubscriptionPaymentRequest;
import com.cooked.backend.dto.request.UpdateSubscriptionPlanRequest;
import com.cooked.backend.dto.request.IapReceiptRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.SubscriptionPaymentResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.SubscriptionService;
import com.cooked.backend.service.ActivityLogService;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionServiceImpl.class);

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Value("${google.play.service-account:}")
    private String googleServiceAccountBase64;

    @Value("${apple.iap.shared-secret:}")
    private String appleSharedSecret;

    public SubscriptionServiceImpl(SubscriptionPlanRepository planRepository,
                                   UserSubscriptionRepository userSubscriptionRepository,
                                   SubscriptionPaymentRepository subscriptionPaymentRepository,
                                   UserRepository userRepository,
                                   ActivityLogService activityLogService) {
        this.planRepository = planRepository;
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.subscriptionPaymentRepository = subscriptionPaymentRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    private final RestTemplate restTemplate = new RestTemplate();

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
        User user = userRepository.findByEmail(emailFix(userEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (isPremium(user)) {
            throw new BadRequestException("You already have an active subscription. No need to pay again.");
        }

        if ("tok_fail".equals(request.getStripeToken())) {
            throw new BadRequestException("Payment failed: Insufficient funds or card declined.");
        }

        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime latestStart = subscription.getEndDate().isAfter(now) ? subscription.getEndDate() : now;

        long daysToAdd = request.getIsYearly() ? 365 : 30;

        subscription.setEndDate(latestStart.plusDays(daysToAdd));
        subscription.setIsYearly(request.getIsYearly());
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        userSubscriptionRepository.save(subscription);

        // SYNC: Update User entity fields for quick access
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionType(request.getIsYearly() ? SubscriptionType.YEARLY : SubscriptionType.MONTHLY);
        user.setSubscriptionExpiresAt(subscription.getEndDate());
        userRepository.save(user);

        SubscriptionPlan plan = getPlan();
        BigDecimal price = request.getIsYearly() ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setUser(user);
        payment.setAmount(price);
        payment.setPlanType(request.getIsYearly() ? "YEARLY" : "MONTHLY");
        payment.setStatus("SUCCESS");
        payment.setStripePaymentId("simulated_" + request.getStripeToken());
        
        subscriptionPaymentRepository.save(payment);

        activityLogService.logActivity(user, "Subscription Successful",
                "Your subscription has been extended. Thank you for using Cooked!");

        return new MessageResponse("Payment successful, subscription activated/renewed.");
    }

    private String emailFix(String email) {
        return email; // Helper if needed
    }

    @Override
    @Transactional
    public MessageResponse verifyReceipt(String userEmail, IapReceiptRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        // If user is already premium with a different transaction, prevent re-subscribing 
        // Note: We allow restoring if transaction ID matches or if expired.
        if (isPremium(user) && user.getOriginalTransactionId() != null && 
            !user.getOriginalTransactionId().equals(request.getPurchaseToken())) {
             // In a real scenario, we might allow this if they are upgrading, but here we prevent duplicate billing
             log.warn("User {} attempted to verify a new receipt while already premium", userEmail);
        }

        boolean isValid = false;
        boolean isYearly = request.getProductId().toLowerCase().contains("yearly");

        if ("ANDROID".equalsIgnoreCase(request.getPlatform())) {
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

                    if (purchase.getPaymentState() != null && purchase.getPaymentState() == 1) {
                        isValid = true;
                    }
                } else {
                    isValid = true; 
                }
            } catch (Exception e) {
                log.error("Error verifying Google Play receipt: {}", e.getMessage());
                isValid = true; 
            }
        } else if ("IOS".equalsIgnoreCase(request.getPlatform())) {
            isValid = verifyAppleReceipt(request.getPurchaseToken());
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

        // SYNC: Update User entity fields for quick access
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionType(isYearly ? SubscriptionType.YEARLY : SubscriptionType.MONTHLY);
        user.setSubscriptionExpiresAt(subscription.getEndDate());
        user.setOriginalTransactionId(request.getPurchaseToken());
        userRepository.save(user);

        SubscriptionPlan plan = getPlan();
        BigDecimal price = isYearly ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setUser(user);
        payment.setAmount(price);
        payment.setPlanType(isYearly ? "YEARLY" : "MONTHLY");
        payment.setStatus("SUCCESS");
        payment.setStripePaymentId("iap_" + request.getPlatform() + "_" + request.getPurchaseToken().length());
        
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
                .map(payment -> {
                    SubscriptionPaymentResponse res = new SubscriptionPaymentResponse();
                    res.setId(payment.getId());
                    res.setAmount(payment.getAmount());
                    res.setPlanType(payment.getPlanType());
                    res.setStatus(payment.getStatus());
                    res.setStripePaymentId(payment.getStripePaymentId());
                    res.setCreatedAt(payment.getCreatedAt());
                    return res;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void processExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<UserSubscription> expiredSubscriptions = userSubscriptionRepository.findAllByEndDateBeforeAndStatusNot(now,
                SubscriptionStatus.EXPIRED);

        for (UserSubscription sub : expiredSubscriptions) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            userSubscriptionRepository.save(sub);
            
            // Also sync to User entity
            User user = sub.getUser();
            if (user != null) {
                user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                userRepository.save(user);
            }
        }
    }

    @Override
    @Transactional
    public void activatePremium(User user, SubscriptionType type, String transactionId) {
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionType(type);
        user.setOriginalTransactionId(transactionId);
        
        if (type == SubscriptionType.MONTHLY) {
            user.setSubscriptionExpiresAt(LocalDateTime.now().plusMonths(1));
        } else if (type == SubscriptionType.YEARLY) {
            user.setSubscriptionExpiresAt(LocalDateTime.now().plusYears(1));
        }
        
        userRepository.save(user);
    }

    @Override
    public boolean isPremium(User user) {
        if (user.getSubscriptionStatus() == SubscriptionStatus.EXPIRED) {
            return false;
        }
        if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        return user.getSubscriptionStatus() == SubscriptionStatus.ACTIVE;
    }

    @Override
    public boolean hasAiAccess(User user) {
        log.info("[hasAiAccess] Checking access for user: {} (ID: {})", user.getEmail(), user.getId());
        
        // 1. Check UserSubscription entity (Source of Truth)
        UserSubscription sub = userSubscriptionRepository.findByUserId(user.getId()).orElse(null);
        if (sub != null) {
            log.info("[hasAiAccess] UserSubscription found: status={}, endDate={}", sub.getStatus(), sub.getEndDate());
            
            if (sub.getStatus() == SubscriptionStatus.ACTIVE || sub.getStatus() == SubscriptionStatus.TRIAL || sub.getStatus() == SubscriptionStatus.PREMIUM) {
                // Safety buffer of 5 minutes to avoid strict edge cases
                LocalDateTime nowWithBuffer = LocalDateTime.now().minusMinutes(5);
                if (sub.getEndDate() == null || sub.getEndDate().isAfter(nowWithBuffer)) {
                    log.info("[hasAiAccess] Access GRANTED via UserSubscription (Active/Trial/Premium)");
                    return true;
                } else {
                    log.warn("[hasAiAccess] UserSubscription is EXPIRED (endDate: {})", sub.getEndDate());
                }
            }
            
            if (sub.getStatus() == SubscriptionStatus.EXPIRED || sub.getStatus() == SubscriptionStatus.CANCELLED || sub.getStatus() == SubscriptionStatus.FREE) {
                // Check if we are still in the 3-day window from account creation (absolute safety net for new users)
                if (user.getCreatedAt() != null && LocalDateTime.now().isBefore(user.getCreatedAt().plusDays(3))) {
                    log.info("[hasAiAccess] Access GRANTED via 3-day account creation safety net (status: {})", sub.getStatus());
                    return true;
                }
                log.warn("[hasAiAccess] Access DENIED (status: {}, createdAt: {})", sub.getStatus(), user.getCreatedAt());
            }
        } else {
            log.info("[hasAiAccess] No UserSubscription found for user");
        }

        // 2. Check User entity fields (Cached/Quick Access) - only if no subscription record or status is unknown
        log.info("[hasAiAccess] Checking User entity fields: status={}, expiresAt={}", user.getSubscriptionStatus(), user.getSubscriptionExpiresAt());
        if (user.getSubscriptionStatus() == SubscriptionStatus.ACTIVE || 
            user.getSubscriptionStatus() == SubscriptionStatus.TRIAL ||
            user.getSubscriptionStatus() == SubscriptionStatus.PREMIUM) {
            
            if (user.getSubscriptionExpiresAt() == null || user.getSubscriptionExpiresAt().isAfter(LocalDateTime.now())) {
                log.info("[hasAiAccess] Access GRANTED via User entity fields");
                return true;
            }
        }

        // Final fallback: 3-day trial period based on account creation (if no other records found)
        LocalDateTime createdAt = user.getCreatedAt();
        if (createdAt == null) {
            // If createdAt is null, it's likely a very new user whose timestamp hasn't been committed yet
            // or a manual registration in progress. We grant access by default for the first few minutes.
            log.info("[hasAiAccess] User createdAt is null. Assuming fresh registration and granting access.");
            return true;
        }

        LocalDateTime trialEndDate = createdAt.plusDays(3);
        if (LocalDateTime.now().isBefore(trialEndDate)) {
            log.info("[hasAiAccess] Access GRANTED via Trial Safety Net fallback (valid until {})", trialEndDate);
            
            // SYNC: If they don't have a record, or it's FREE, update it to TRIAL to reflect reality
            try {
                UserSubscription currentSub = userSubscriptionRepository.findByUserId(user.getId()).orElse(null);
                if (currentSub == null) {
                    log.info("[hasAiAccess] Creating new UserSubscription with TRIAL status for safety net");
                    UserSubscription newSub = new UserSubscription();
                    newSub.setUser(user);
                    newSub.setStatus(SubscriptionStatus.TRIAL);
                    newSub.setEndDate(trialEndDate);
                    userSubscriptionRepository.save(newSub);
                } else if (currentSub.getStatus() == SubscriptionStatus.FREE) {
                    log.info("[hasAiAccess] Updating existing FREE subscription to TRIAL for safety net");
                    currentSub.setStatus(SubscriptionStatus.TRIAL);
                    currentSub.setEndDate(trialEndDate);
                    userSubscriptionRepository.save(currentSub);
                }
            } catch (Exception e) {
                log.warn("[hasAiAccess] Failed to auto-sync subscription: {}", e.getMessage());
            }
            return true;
        }
        
        return false;
    }

    private boolean verifyAppleReceipt(String receiptData) {
        log.info("Verifying Apple Receipt");
        String url = "https://buy.itunes.apple.com/verifyReceipt";
        
        if (appleSharedSecret == null || appleSharedSecret.trim().isEmpty() || "VOTRE_SHARED_SECRET".equals(appleSharedSecret)) {
            log.warn("Apple Shared Secret is not configured. Defaulting to VALID for testing.");
            return true;
        }

        try {
            boolean success = callAppleVerify(url, receiptData);
            if (!success) {
                // If status is 21007, retry with Sandbox
                url = "https://sandbox.itunes.apple.com/verifyReceipt";
                success = callAppleVerify(url, receiptData);
            }
            return success;
        } catch (Exception e) {
            log.error("Apple receipt verification error: {}", e.getMessage());
            return false;
        }
    }

    private boolean callAppleVerify(String url, String receiptData) {
        try {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("receipt-data", receiptData);
            body.put("password", appleSharedSecret);

            org.springframework.http.ResponseEntity<java.util.Map> response = restTemplate.postForEntity(url, body, java.util.Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Integer status = (Integer) response.getBody().get("status");
                if (status != null && status == 0) {
                    return true;
                }
                if (status != null && status == 21007) {
                    return false; // Signal retry with sandbox
                }
                log.warn("Apple verification returned status: {}", status);
            }
        } catch (Exception e) {
            log.error("Error calling Apple verify: {}", e.getMessage());
        }
        return false;
    }

    private SubscriptionPlan ensureDefaultPlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setMonthlyPrice(BigDecimal.valueOf(20));
        plan.setYearlyPrice(BigDecimal.valueOf(200));
        plan.setYearlyDiscountPercentage(16.67);
        plan.setTrialDays(3);
        return planRepository.save(plan);
    }
}
