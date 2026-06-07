package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.IapReceiptRequest;
import com.cooked.backend.dto.request.SubscriptionPaymentRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.SubscriptionPlanRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSubscriptionRepository;
import com.cooked.backend.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private SubscriptionPaymentRepository subscriptionPaymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private User dummyUser;
    private UUID dummyUserId;
    private UserSubscription dummySubscription;
    private SubscriptionPlan dummyPlan;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "appleSharedSecret", "dummy-secret");

        dummyUserId = UUID.randomUUID();
        dummyUser = User.builder()
                .id(dummyUserId)
                .email("test@example.com")
                .subscriptionStatus(SubscriptionStatus.FREE)
                .build();

        dummySubscription = new UserSubscription();
        dummySubscription.setUser(dummyUser);
        dummySubscription.setStatus(SubscriptionStatus.FREE);
        dummySubscription.setEndDate(LocalDateTime.now().minusDays(1)); // Expired

        dummyPlan = new SubscriptionPlan();
        dummyPlan.setMonthlyPrice(BigDecimal.valueOf(19.99));
        dummyPlan.setYearlyPrice(BigDecimal.valueOf(199.99));
    }

    @Test
    void testGetPlan_ReturnsDefault() {
        when(planRepository.findAll()).thenReturn(Collections.singletonList(dummyPlan));
        SubscriptionPlan result = subscriptionService.getPlan();
        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(19.99), result.getMonthlyPrice());
    }

    @Test
    void testPaySubscription_Success() {
        SubscriptionPaymentRequest request = new SubscriptionPaymentRequest();
        request.setStripeToken("tok_123");
        request.setIsYearly(false);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(userSubscriptionRepository.findByUserId(dummyUserId)).thenReturn(Optional.of(dummySubscription));
        when(planRepository.findAll()).thenReturn(Collections.singletonList(dummyPlan));

        MessageResponse response = subscriptionService.paySubscription("test@example.com", request);

        assertEquals("Payment successful, subscription activated/renewed.", response.getMessage());
        assertEquals(SubscriptionStatus.ACTIVE, dummySubscription.getStatus());
        assertEquals(SubscriptionStatus.ACTIVE, dummyUser.getSubscriptionStatus());
        
        verify(userSubscriptionRepository).save(dummySubscription);
        verify(userRepository).save(dummyUser);
        verify(subscriptionPaymentRepository).save(any(SubscriptionPayment.class));
        verify(activityLogService).logActivity(eq(dummyUser), anyString(), anyString());
    }

    @Test
    void testPaySubscription_AlreadyPremium_ThrowsException() {
        dummyUser.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        dummyUser.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(10));
        
        SubscriptionPaymentRequest request = new SubscriptionPaymentRequest();
        request.setStripeToken("tok_123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(userSubscriptionRepository.findByUserId(dummyUserId)).thenReturn(Optional.of(dummySubscription));

        assertThrows(BadRequestException.class, () -> subscriptionService.paySubscription("test@example.com", request));
    }

    @Test
    void testPaySubscription_CardDeclined_ThrowsException() {
        SubscriptionPaymentRequest request = new SubscriptionPaymentRequest();
        request.setStripeToken("tok_fail");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(userSubscriptionRepository.findByUserId(dummyUserId)).thenReturn(Optional.of(dummySubscription));

        assertThrows(BadRequestException.class, () -> subscriptionService.paySubscription("test@example.com", request));
    }

    @Test
    void testHasAiAccess_ActiveSubscription() {
        dummySubscription.setStatus(SubscriptionStatus.ACTIVE);
        dummySubscription.setEndDate(LocalDateTime.now().plusDays(10));

        when(userSubscriptionRepository.findByUserId(dummyUserId)).thenReturn(Optional.of(dummySubscription));

        assertTrue(subscriptionService.hasAiAccess(dummyUser));
    }

    @Test
    void testHasAiAccess_ExpiredSubscription() {
        dummySubscription.setStatus(SubscriptionStatus.EXPIRED);
        dummySubscription.setEndDate(LocalDateTime.now().minusDays(1));
        // Account created long time ago so trial safety net fails
        dummyUser.setCreatedAt(LocalDateTime.now().minusDays(10));

        when(userSubscriptionRepository.findByUserId(dummyUserId)).thenReturn(Optional.of(dummySubscription));

        assertFalse(subscriptionService.hasAiAccess(dummyUser));
    }

    @Test
    void testHasAiAccess_FreshRegistrationTrialSafetyNet() {
        // No subscription record exists yet
        dummyUser.setCreatedAt(LocalDateTime.now());
        when(userSubscriptionRepository.findByUserId(dummyUserId)).thenReturn(Optional.empty());

        assertTrue(subscriptionService.hasAiAccess(dummyUser));
    }
}
