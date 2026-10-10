package com.cooked.backend.service.impl;

import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.UserSubscription;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSubscriptionRepository;
import com.cooked.backend.service.RevenueCatApiClient;
import com.cooked.backend.service.RevenueCatApiClient.PremiumEntitlement;
import com.cooked.backend.service.SubscriptionVerificationService;
import com.cooked.backend.util.StoreDates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionVerificationServiceImpl implements SubscriptionVerificationService {

    private static final int MAX_TRACKED_USERS = 10_000;

    private final RevenueCatApiClient revenueCatApiClient;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    /** Last store lookup per user, to rate-limit RevenueCat calls. */
    private final Map<UUID, Instant> lastCheck = new ConcurrentHashMap<>();

    @Override
    public boolean isAvailable() {
        return revenueCatApiClient.isConfigured();
    }

    @Override
    @Transactional
    public boolean refreshFromStore(User user, Duration minInterval) {
        if (user == null || user.getId() == null || !isAvailable()) return false;

        Instant now = Instant.now();
        Instant previous = lastCheck.get(user.getId());
        if (previous != null && previous.plus(minInterval).isAfter(now)) {
            return false;
        }
        if (lastCheck.size() > MAX_TRACKED_USERS) lastCheck.clear();
        lastCheck.put(user.getId(), now);

        Optional<PremiumEntitlement> premium;
        try {
            // The app logs into RevenueCat with our user id, so that is the
            // only id we look up - never one supplied by the client.
            premium = revenueCatApiClient.fetchActivePremium(user.getId().toString());
        } catch (Exception e) {
            log.error("[SubVerify] RevenueCat lookup failed for {}: {}", user.getEmail(), e.getMessage());
            return false;
        }

        if (premium.isEmpty()) {
            log.info("[SubVerify] RevenueCat: no active premium for {}", user.getEmail());
            return false;
        }

        apply(user, premium.get());
        log.info("[SubVerify] RevenueCat confirmed premium for {} (product={}, expires={})",
                user.getEmail(), premium.get().productId(), user.getSubscriptionExpiresAt());
        return true;
    }

    private void apply(User user, PremiumEntitlement premium) {
        LocalDateTime expiresAt = StoreDates.toLocal(premium.expiresAt());
        boolean yearly = premium.productId() != null && premium.productId().toLowerCase().contains("year");

        if (user.getSubscriptionStatus() != SubscriptionStatus.INFINITE) {
            user.setSubscriptionStatus(premium.trial() ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE);
            user.setSubscriptionExpiresAt(expiresAt);
        }
        if (premium.productId() != null) {
            if (yearly) user.setSubscriptionType(SubscriptionType.YEARLY);
            else if (premium.productId().toLowerCase().contains("month")) user.setSubscriptionType(SubscriptionType.MONTHLY);
        }
        userRepository.save(user);

        // Keep the subscription record (shown by /subscriptions/me) in step,
        // otherwise it keeps saying EXPIRED after the trial ended.
        UserSubscription sub = userSubscriptionRepository.findByUserId(user.getId()).orElse(null);
        if (sub != null && sub.getStatus() != SubscriptionStatus.INFINITE) {
            sub.setStatus(premium.trial() ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE);
            // endDate is NOT NULL; a lifetime entitlement has no expiry.
            sub.setEndDate(expiresAt != null ? expiresAt : LocalDateTime.now().plusYears(100));
            if (premium.productId() != null) sub.setIsYearly(yearly);
            userSubscriptionRepository.save(sub);
        }
    }
}
