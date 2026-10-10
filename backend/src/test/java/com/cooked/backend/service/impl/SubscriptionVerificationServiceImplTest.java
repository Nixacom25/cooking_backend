package com.cooked.backend.service.impl;

import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.UserSubscription;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSubscriptionRepository;
import com.cooked.backend.service.RevenueCatApiClient;
import com.cooked.backend.service.RevenueCatApiClient.PremiumEntitlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionVerificationServiceImplTest {

    @Mock private RevenueCatApiClient revenueCatApiClient;
    @Mock private UserRepository userRepository;
    @Mock private UserSubscriptionRepository userSubscriptionRepository;
    @InjectMocks private SubscriptionVerificationServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("payer@example.com");
        // State seen in production: trial ended, RevenueCat sync never applied.
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionExpiresAt(LocalDateTime.now().minusDays(8));
    }

    @Test
    void confirmedPremiumRestoresAccessAndRecord() {
        Instant expires = Instant.parse("2027-10-02T05:23:44Z");
        UserSubscription sub = new UserSubscription();
        sub.setStatus(SubscriptionStatus.EXPIRED);
        when(revenueCatApiClient.isConfigured()).thenReturn(true);
        when(revenueCatApiClient.fetchActivePremium(user.getId().toString()))
                .thenReturn(Optional.of(new PremiumEntitlement("cooked_yearly", expires, false)));
        when(userSubscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.of(sub));

        assertTrue(service.refreshFromStore(user, Duration.ofSeconds(60)));

        LocalDateTime expected = LocalDateTime.ofInstant(expires, ZoneId.systemDefault());
        assertEquals(SubscriptionStatus.ACTIVE, user.getSubscriptionStatus());
        assertEquals(expected, user.getSubscriptionExpiresAt());
        assertEquals(SubscriptionType.YEARLY, user.getSubscriptionType());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals(expected, sub.getEndDate());
        verify(userRepository).save(user);
        verify(userSubscriptionRepository).save(sub);
    }

    @Test
    void notPremiumChangesNothing() {
        when(revenueCatApiClient.isConfigured()).thenReturn(true);
        when(revenueCatApiClient.fetchActivePremium(any())).thenReturn(Optional.empty());

        assertFalse(service.refreshFromStore(user, Duration.ofSeconds(60)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void outageIsNotTreatedAsPremium() {
        when(revenueCatApiClient.isConfigured()).thenReturn(true);
        when(revenueCatApiClient.fetchActivePremium(any())).thenThrow(new IllegalStateException("down"));

        assertFalse(service.refreshFromStore(user, Duration.ofSeconds(60)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void repeatedChecksAreThrottled() {
        when(revenueCatApiClient.isConfigured()).thenReturn(true);
        when(revenueCatApiClient.fetchActivePremium(any())).thenReturn(Optional.empty());

        service.refreshFromStore(user, Duration.ofSeconds(60));
        service.refreshFromStore(user, Duration.ofSeconds(60));
        verify(revenueCatApiClient, times(1)).fetchActivePremium(any());
    }

    @Test
    void notConfiguredNeverCallsRevenueCat() {
        when(revenueCatApiClient.isConfigured()).thenReturn(false);
        assertFalse(service.refreshFromStore(user, Duration.ofSeconds(60)));
        verify(revenueCatApiClient, never()).fetchActivePremium(any());
    }
}
