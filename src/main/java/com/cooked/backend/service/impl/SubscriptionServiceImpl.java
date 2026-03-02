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

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

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

        activityLogService.logActivity(user, "Subscription Successful",
                "Your subscription has been extended. Thank you for using Cooked!");

        return new MessageResponse("Payment successful, subscription activated/renewed.");
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
                .trialDays(30)
                .build();
        return planRepository.save(plan);
    }
}
