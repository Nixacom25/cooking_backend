package com.cooked.backend.mapper;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private User client(SubscriptionStatus status) {
        return User.builder().email("u@cookedapp.com").password("x")
                .role(Role.CLIENT).subscriptionStatus(status).build();
    }

    @Test
    void abandonedAtPaywallIsNotCompleted() {
        assertFalse(UserMapper.onboardingCompleted(client(SubscriptionStatus.FREE)));
    }

    @Test
    void expiredOrFinishedAccountsAreCompleted() {
        assertTrue(UserMapper.onboardingCompleted(client(SubscriptionStatus.EXPIRED)));

        User skippedTrial = client(SubscriptionStatus.FREE);
        skippedTrial.setWelcomeEmailSent(true);
        assertTrue(UserMapper.onboardingCompleted(skippedTrial));

        User lapsed = client(SubscriptionStatus.FREE);
        lapsed.setSubscriptionExpiresAt(LocalDateTime.now().minusDays(3));
        assertTrue(UserMapper.onboardingCompleted(lapsed));
    }
}
